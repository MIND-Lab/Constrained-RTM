package yang.weiwei.lda;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import javax.swing.plaf.synth.SynthSeparatorUI;

import cc.mallet.util.Randoms;
import yang.weiwei.lda.util.CLDADoc;
import yang.weiwei.lda.util.LDADoc;
import yang.weiwei.lda.util.LDATopic;
import yang.weiwei.lda.util.LDAWord;
import yang.weiwei.util.MathUtil;
import yang.weiwei.util.format.Fourmat;
import yang.weiwei.util.IOUtil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

/**
 * Latent Dirichlet Allocation
 *
 * @author Yang Weiwei
 *
 */
public class LDA {
	public static final int TRAIN = 0;
	public static final int TEST = 1;

	/** Parameter object */
	public final LDAParam param;

	protected static Randoms randoms;
	protected static Gson gson;

	@Expose
	protected double alpha[];
	protected double updateDenom;

	protected int numDocs;
	protected int numWords;
	protected int numTestWords;
	protected final int type;

	protected ArrayList<LDADoc> corpus;
	protected LDATopic topics[];
	protected ArrayList<LDAWord> vocabulary;

	protected double theta[][];
	@Expose
	protected double phi[][];

	protected double logLikelihood;
	protected double perplexity;

	protected double epsilon = 1.0;

	/**
	 * Read corpus
	 *
	 * @param corpusFileName Corpus file name
	 * @throws IOException IOException
	 */
	public void readCorpus(String corpusFileName) throws IOException {
		readCorpus(corpusFileName, true);

	}

	public void readCorpus(String corpusFileName, boolean indexed) throws IOException {
		vocabulary = new ArrayList<LDAWord>();
		for (String s : param.vocabList) {
			vocabulary.add(new LDAWord(s));
		}

		BufferedReader br = new BufferedReader(new FileReader(corpusFileName));
		String line;
		while ((line = br.readLine()) != null) {
			if (indexed) {
				if (param.constrained)
					corpus.add(new CLDADoc(line, param.numTopics, param.numVocab));
				else
					corpus.add(new LDADoc(line, param.numTopics, param.numVocab));
			} else {
				if (param.constrained)
					corpus.add(new CLDADoc(line, param.numTopics, param.vocabMap));
				else
					corpus.add(new LDADoc(line, param.numTopics, param.vocabMap));

			}
		}
		br.close();
		numDocs = corpus.size();
	}

	public void readDocConstraints(String constraintsFileName) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(constraintsFileName));
		String line;
		while ((line = br.readLine()) != null) {
			String[] splittedLine = line.split("\\s");
			String constraintType = splittedLine[0];
			int doc1 = Integer.parseInt(splittedLine[1]);
			int doc2 = Integer.parseInt(splittedLine[2]);

			switch (constraintType) {
			case "M":
				((CLDADoc) corpus.get(doc1)).addMustLink(doc2);
				((CLDADoc) corpus.get(doc2)).addMustLink(doc1);
				break;
			case "C":
				((CLDADoc) corpus.get(doc1)).addCannotLink(doc2);
				((CLDADoc) corpus.get(doc2)).addCannotLink(doc1);
				break;
			default:
				// discard line
			}
		}
		br.close();

	}

	public void readVocabConstraints(String constraintsFileName) throws IOException {
		System.out.println("READ VOCAB CONSTRAINTS");
		if (!new File(constraintsFileName).exists()) {
			new File(constraintsFileName).createNewFile();
			System.out.println("creato file constraints vuoto: " + constraintsFileName);
		}
		BufferedReader br = new BufferedReader(new FileReader(constraintsFileName));
		String line;
		while ((line = br.readLine()) != null) {
			String[] splittedLine = line.split("\\t");
			String constraintType = splittedLine[0];
			// String word1 = splittedLine[1];
			// String word2 = splittedLine[2];

			int wordIndex1 = param.vocabMap.get(splittedLine[1]);
			int wordIndex2 = param.vocabMap.get(splittedLine[2]);

			switch (constraintType) {
			case "M":
				System.out.println(splittedLine[1] + " " + splittedLine[2]);
				vocabulary.get(wordIndex1).addMustlink(wordIndex2);
				vocabulary.get(wordIndex2).addMustlink(wordIndex1);
				break;
			case "C":
				vocabulary.get(wordIndex1).addCannotlink(wordIndex2);
				vocabulary.get(wordIndex2).addCannotlink(wordIndex1);
				break;
			default:
				// discard line
			}
		}
		br.close();

	}

	protected void printParam() {
		IOUtil.print("Running " + this.getClass().getSimpleName());
		if (type == TRAIN) {
			IOUtil.println(" Training");
		} else {
			IOUtil.println(" Test");
		}
		IOUtil.println("\t#docs: " + numDocs);
		param.printBasicParam("\t");
	}

	/**
	 * Initialize LDA member variables
	 *
	 * @throws IOException
	 */
	public void initialize() throws IOException {
		initDocVariables();
		initTopicAssigns();
		printParam();
		if (param.metricsFileName.length() > 0) {
			BufferedWriter bw = new BufferedWriter(new FileWriter(param.metricsFileName));
			bw.write("logLikelihood;perplexity");
			bw.newLine();
			bw.close();
		}
	}

	protected void initTopicAssigns() {
		for (LDADoc doc : corpus) {
			int interval = getSampleInterval();
			for (int token = 0; token < doc.docLength(); token += interval) {
				int topic = randoms.nextInt(param.numTopics);
				doc.assignTopic(token, topic);

				int word = doc.getWord(token);
				topics[topic].addVocab(word);
			}
		}

		for (int j = 0; j < param.vocabList.size(); j++) {
			int count = 0;
			for (int i = 0; i < param.numTopics; i++) {
				count = count + topics[i].getVocabCount(j);
			}
			vocabulary.get(j).setCount(count);
			// System.out.println("count vocab j= " + j + " word= " + param.vocabList.get(j)
			// + " " + vocabulary.get(j).getCount());
		}
	}

	/**
	 * Initialize LDA member variables with user-provided topic assignments (may be
	 * unstable)
	 *
	 * @param topicAssignFileName Topic assignment file name
	 * @throws IOException IOException
	 */
	public void initialize(String topicAssignFileName) throws IOException {
		initDocVariables();
		initTopicAssigns(topicAssignFileName);
		printParam();
	}

	protected void initTopicAssigns(String topicAssignFileName) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(topicAssignFileName));
		String line, seg[];
		for (int doc = 0; doc < numDocs; doc++) {
			line = br.readLine();
			seg = line.split(" ");
			int interval = getSampleInterval();
			for (int token = 0; token < corpus.get(doc).docLength(); token += interval) {
				int topic = randoms.nextInt(param.numTopics);
				if (token < seg.length && seg[token].length() > 0) {
					topic = Integer.valueOf(seg[token]);
				}
				corpus.get(doc).assignTopic(token, topic);

				int word = corpus.get(doc).getWord(token);
				topics[topic].addVocab(word);
			}
		}
		br.close();
	}

	protected void initDocVariables() {
		updateDenom = 0.0;
		numWords = 0;
		for (int doc = 0; doc < numDocs; doc++) {
			numWords += corpus.get(doc).docLength();
			int sampleSize = getSampleSize(corpus.get(doc).docLength());
			updateDenom += (double) (sampleSize) / (double) (sampleSize + param.alpha * param.numTopics);
		}
		theta = new double[numDocs][param.numTopics];
		getNumTestWords();

	}

	protected void printMetrics() {
		IOUtil.print("Finished " + this.getClass().getSimpleName() + " ");
		if (type == TRAIN) {
			IOUtil.println("Training");
		} else {
			IOUtil.println("Test");
		}
		IOUtil.println("Log Likelihood: " + format(logLikelihood));
		IOUtil.println("Perplexity: " + format(perplexity));
		IOUtil.print("Topic Coherence: ");
	}

	/**
	 * Sample for given number of iterations
	 *
	 * @param numIters Number of iterations
	 * @throws IOException
	 */
	public void sample(int numIters) throws IOException {
		sample(numIters, 0);
	}

	public void sample(int numIters, int burnin) throws IOException {
		for (int iteration = 1; iteration <= numIters; iteration++) {
			// long startTime = System.currentTimeMillis();
			for (int doc = 0; doc < numDocs; doc++) {
				sampleDoc(doc);
			}
			// long endTime = System.currentTimeMillis();
			// System.out.println("docs: "+ (endTime - startTime));
			computeLogLikelihood();
			perplexity = Math.exp(-logLikelihood / numTestWords);
			if (burnin > 0)
				burnin--;
			else {
				if (param.metricsFileName.length() > 0)
					writeMetrics(param.metricsFileName);
			}

			if (param.verbose && iteration % 100 == 0) {
				IOUtil.println(
						"<" + iteration + ">" + "\tLog-LLD: " + format(logLikelihood) + "\tPPX: " + format(perplexity));
			}
			/*
			 * if (param.updateAlpha && iteration % param.updateAlphaInterval == 0 && type
			 * == TRAIN) { updateHyperParam(); }
			 */
		}

		if (type == TRAIN && param.verbose) {

			for (int topic = 0; topic < param.numTopics; topic++) {
				IOUtil.println(topWordsByFreq(topic, 10));
			}
		}
		System.out.println("computo phi");
		computePhi();
		printMetrics();
	}

	protected void sampleDoc(int doc) {
		int oldTopic, newTopic, interval = getSampleInterval();
		for (int token = 0; token < corpus.get(doc).docLength(); token += interval) {
			oldTopic = unassignTopic(doc, token);
			newTopic = sampleTopic(doc, token, oldTopic);
			assignTopic(doc, token, newTopic);
		}
	}

	protected int unassignTopic(int doc, int token) {
		int oldTopic = corpus.get(doc).getTopicAssign(token);
		int word = corpus.get(doc).getWord(token);
		corpus.get(doc).unassignTopic(token);
		topics[oldTopic].removeVocab(word);
		return oldTopic;
	}

	protected int sampleTopic(int doc, int token, int oldTopic) {
		int word = corpus.get(doc).getWord(token);
		double topicScores[] = new double[param.numTopics];
		for (int topic = 0; topic < param.numTopics; topic++) {
			topicScores[topic] = topicUpdating(doc, topic, word);
		}

		int newTopic = MathUtil.selectLogDiscrete(topicScores);
		if (newTopic == -1) {
			newTopic = oldTopic;
			for (int topic = 0; topic < param.numTopics; topic++) {
				IOUtil.println(format(topicScores[topic]));
			}
		}

		return newTopic;
	}

	protected void assignTopic(int doc, int token, int newTopic) {
		int word = corpus.get(doc).getWord(token);
		corpus.get(doc).assignTopic(token, newTopic);
		topics[newTopic].addVocab(word);
	}

	protected double topicUpdating(int doc, int topic, int vocab) {

		double score = 0.0;
		if (type == TRAIN) {
			score = Math.log((alpha[topic] + corpus.get(doc).getTopicCount(topic))
					* (param.beta + topics[topic].getVocabCount(vocab))
					/ (param.beta * param.numVocab + topics[topic].getTotalTokens()));
		} else {
			score = Math.log((alpha[topic] + corpus.get(doc).getTopicCount(topic)) * phi[topic][vocab]);
		}

		if (param.constrained) {
			score += computeLogPotential(doc, topic, vocab);
		}
		return score;
	}

	protected double computeLogPotential(int doc, int topic, int vocab) {
		double logPotential = 0;

		if (!((CLDADoc) corpus.get(doc)).mustLink.isEmpty()) {
			for (int d : ((CLDADoc) corpus.get(doc)).mustLink) {
				double ndt = corpus.get(d).getTopicCount(topic);
				double nd = corpus.get(d).docLength();
				if (param.newfun)
					logPotential += Math.log(epsilon + ndt / nd);
				else
					logPotential += Math.log(Math.max(param.lambda, corpus.get(d).getTopicCount(topic)));

			}
		}

		if (!((CLDADoc) corpus.get(doc)).cannotLink.isEmpty()) {
			for (int d : ((CLDADoc) corpus.get(doc)).cannotLink) {
				double ndt = corpus.get(d).getTopicCount(topic);
				double nd = corpus.get(d).docLength();
				if (param.newfun)
					logPotential -= Math.log(epsilon + ndt / nd);
				else
					logPotential -= Math.log(Math.max(param.lambda, corpus.get(d).getTopicCount(topic)));
			}
		}

		// word correlation
		for (int w : vocabulary.get(vocab).getMustlinks()) {
			// lambda da cambiar
			// System.out.println(w + " " + vocab);
			if (param.word_norm_fun) {
				double wCount = (double) vocabulary.get(w).getCount();
				if (wCount > 0) {
					logPotential += Math.log(param.lambda + (topics[topic].getVocabCount(w) / wCount));
				}
			} else
				logPotential += Math.log(Math.max(param.lambda, topics[topic].getVocabCount(w)));

		}

		return logPotential;
	}

	protected double computeLogPotential(int doc, int topic) {
		double logPotential = 0;
		if (!((CLDADoc) corpus.get(doc)).mustLink.isEmpty()) {
			for (int d : ((CLDADoc) corpus.get(doc)).mustLink) {
				double ndt = corpus.get(d).getTopicCount(topic);
				double nd = corpus.get(d).docLength();
				if (param.newfun)
					logPotential += Math.log(epsilon + ndt / nd);
				else
					logPotential += Math.log(Math.max(param.lambda, corpus.get(d).getTopicCount(topic)));

			}
		}

		if (!((CLDADoc) corpus.get(doc)).cannotLink.isEmpty()) {
			for (int d : ((CLDADoc) corpus.get(doc)).cannotLink) {
				double ndt = corpus.get(d).getTopicCount(topic);
				double nd = corpus.get(d).docLength();
				if (param.newfun)
					logPotential -= Math.log(epsilon + ndt / nd);
				else
					logPotential -= Math.log(Math.max(param.lambda, corpus.get(d).getTopicCount(topic)));
			}
		}
		return logPotential;
	}

	protected void updateHyperParam() {
		double oldAlpha[] = new double[param.numTopics];
		for (int topic = 0; topic < param.numTopics; topic++) {
			oldAlpha[topic] = alpha[topic];
		}

		double numer;
		for (int topic = 0; topic < param.numTopics; topic++) {
			numer = 0.0;
			for (LDADoc doc : corpus) {
				numer += (double) (doc.getTopicCount(topic)) / (double) (doc.getTopicCount(topic) + oldAlpha[topic]);
			}
			alpha[topic] = oldAlpha[topic] * numer / updateDenom;
		}

		double newAlphaSum = 0.0;
		for (int topic = 0; topic < param.numTopics; topic++) {
			newAlphaSum += alpha[topic];
		}
		for (int topic = 0; topic < param.numTopics; topic++) {
			alpha[topic] *= param.alpha * param.numTopics / newAlphaSum;

		}
	}

	protected void computeLogLikelihood() {
		computeTheta();
		if (type == TRAIN) {
			computePhi();
		}

		int word;
		double sum;
		logLikelihood = 0.0;
		for (int doc = 0; doc < numDocs; doc++) {
			int startPos = getStartPos();
			int interval = getSampleInterval();
			for (int token = startPos; token < corpus.get(doc).docLength(); token += interval) {
				word = corpus.get(doc).getWord(token);
				sum = 0.0;
				for (int topic = 0; topic < param.numTopics; topic++) {
					sum += theta[doc][topic] * phi[topic][word];
				}
				logLikelihood += Math.log(sum);
			}
		}
	}

	protected void computeTheta() {
		for (int doc = 0; doc < numDocs; doc++) {
			for (int topic = 0; topic < param.numTopics; topic++) {
				theta[doc][topic] = (alpha[topic] + corpus.get(doc).getTopicCount(topic))
						/ (param.alpha * param.numTopics + getSampleSize(corpus.get(doc).docLength()));
			}
		}
	}

	protected void computePhi() {
		for (int topic = 0; topic < param.numTopics; topic++) {
			for (int vocab = 0; vocab < param.numVocab; vocab++) {
				phi[topic][vocab] = (param.beta + topics[topic].getVocabCount(vocab))
						/ (param.beta * param.numVocab + topics[topic].getTotalTokens());
			}
		}
	}

	protected void getNumTestWords() {
		if (type == TRAIN) {
			numTestWords = numWords;
		} else {
			numTestWords = 0;
			for (LDADoc doc : corpus) {
				numTestWords += doc.docLength() / 2;
			}
		}
	}

	protected int getStartPos() {
		return (type == TRAIN ? 0 : 1);
	}

	protected int getSampleSize(int docLength) {
		return (type == TRAIN ? docLength : (docLength + 1) / 2);
	}

	protected int getSampleInterval() {
		return (type == TRAIN ? 1 : 2);
	}

	/**
	 * Get document distribution over topics
	 *
	 * @return Document distribution over topics
	 */
	public double[][] getDocTopicDist() {
		return theta.clone();
	}

	/**
	 * Get topic distribution over words
	 *
	 * @return Topic distribution over words
	 */
	public double[][] getTopicVocabDist() {
		return phi.clone();
	}

	/**
	 * Get number of documents
	 *
	 * @return Number of documents
	 */
	public int getNumDocs() {
		return numDocs;
	}

	/**
	 * Get number of tokens in the corpus
	 *
	 * @return Number of tokens
	 */
	public int getNumWords() {
		return numWords;
	}

	/**
	 * Get a specific document
	 *
	 * @param doc Document number
	 * @return Corresponding document object
	 */
	public LDADoc getDoc(int doc) {
		return corpus.get(doc);
	}

	/**
	 * Get a specific topic
	 *
	 * @param topic Topic number
	 * @return Corresponding topic object
	 */
	public LDATopic getTopic(int topic) {
		return topics[topic];
	}

	/**
	 * Get log likelihood
	 *
	 * @return Log likelihood
	 */
	public double getLogLikelihood() {
		return logLikelihood;
	}

	/**
	 * Get perplexity
	 *
	 * @return Perplexity
	 */
	public double getPerplexity() {
		return perplexity;
	}

	/**
	 * Get documents' number of tokens assigned to every topic
	 *
	 * @return Documents' number of tokens assigned to every topic
	 */
	public int[][] getDocTopicCounts() {
		int docTopicCounts[][] = new int[numDocs][param.numTopics];
		for (int doc = 0; doc < numDocs; doc++) {
			for (int topic = 0; topic < param.numTopics; topic++) {
				docTopicCounts[doc][topic] = corpus.get(doc).getTopicCount(topic);
			}
		}
		return docTopicCounts;
	}

	/**
	 * Get tokens' topic assignments
	 *
	 * @return Tokens' topic assignments
	 */
	public int[][] getTokenTopicAssign() {
		int tokenTopicAssign[][] = new int[numDocs][];
		for (int doc = 0; doc < numDocs; doc++) {
			tokenTopicAssign[doc] = new int[corpus.get(doc).docLength()];
			for (int token = 0; token < corpus.get(doc).docLength(); token++) {
				tokenTopicAssign[doc][token] = corpus.get(doc).getTopicAssign(token);
			}
		}
		return tokenTopicAssign;
	}

	/**
	 * Get a topic's top words (with highest number of assignments)
	 *
	 * @param topic       Topic number
	 * @param numTopWords Number of top words
	 * @return Given topic's top words
	 */
	public String topWordsByFreq(int topic, int numTopWords) {
		String result = "Topic " + topic + ":";
		LDAWord words[] = new LDAWord[param.numVocab];
		for (int vocab = 0; vocab < param.numVocab; vocab++) {
			words[vocab] = new LDAWord(param.vocabList.get(vocab), topics[topic].getVocabCount(vocab));
		}

		Arrays.sort(words);
		for (int i = 0; i < numTopWords; i++) {
			result += "   " + words[i];
		}
		return result;
	}

	/**
	 * Get a topic's top words (with highest weight)
	 *
	 * @param topic       Topic number
	 * @param numTopWords Number of top words
	 * @return Given topic's top words
	 */
	public String topWordsByWeight(int topic, int numTopWords) {
		String result = "Topic " + topic + ":";
		LDAWord words[] = new LDAWord[param.numVocab];
		for (int vocab = 0; vocab < param.numVocab; vocab++) {
			words[vocab] = new LDAWord(param.vocabList.get(vocab), phi[topic][vocab]);
		}

		Arrays.sort(words);
		for (int i = 0; i < numTopWords; i++) {
			result += "   " + words[i];
		}
		return result;
	}

	/**
	 * Write topics' top words to file
	 *
	 * @param resultFileName Result file name
	 * @param numTopWords    Number of top words
	 * @throws IOException IOException
	 */
	public void writeResult(String resultFileName, int numTopWords) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(resultFileName));
		for (int topic = 0; topic < param.numTopics; topic++) {
			bw.write(topWordsByFreq(topic, numTopWords));
			bw.newLine();
		}
		bw.close();
	}

	public void writeMetrics(String metricFileName) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(metricFileName, true));
		bw.write(logLikelihood + ";" + perplexity);
		bw.newLine();
		bw.close();
	}

	/**
	 * Write document distribution over topics to file
	 *
	 * @param docTopicDistFileName Distribution file name
	 * @throws IOException IOException
	 */
	public void writeDocTopicDist(String docTopicDistFileName) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(docTopicDistFileName));
		IOUtil.writeMatrix(bw, theta);
		bw.close();
	}

	public void writeVocabTopicCounts(String vocabTopicCountsFileName) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(vocabTopicCountsFileName));
		int vocabTopicCounts[][] = new int[param.vocabList.size()][param.numTopics];
		for (int i = 0; i < param.numTopics; i++) {
			for (int j = 0; j < param.vocabList.size(); j++) {
				vocabTopicCounts[j][i] = topics[i].getVocabCount(j);
			}
		}
		IOUtil.writeMatrix(bw, vocabTopicCounts);
		bw.close();
	}

	/**
	 * Write documents' number of tokens assigned to topics to file
	 *
	 * @param topicCountFileName Documents' topic count file name
	 * @throws IOException IOException
	 */
	public void writeDocTopicCounts(String topicCountFileName) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(topicCountFileName));
		IOUtil.writeMatrix(bw, getDocTopicCounts());
		bw.close();
	}

	/**
	 * Write tokens' topic assignments to file
	 *
	 * @param topicAssignFileName Topic assignment file name
	 * @throws IOException IOException
	 */
	public void writeTokenTopicAssign(String topicAssignFileName) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(topicAssignFileName));
		IOUtil.writeMatrix(bw, getTokenTopicAssign());
		bw.close();
	}

	/**
	 * Write model to file
	 *
	 * @param modelFileName Model file name
	 * @throws IOException IOException
	 */
	public void writeModel(String modelFileName) throws IOException {
		if (type == TRAIN) {
			BufferedWriter bw = new BufferedWriter(new FileWriter(modelFileName));
			bw.write(gson.toJson(this));
			bw.close();
		} else {
			BufferedWriter bw = new BufferedWriter(new FileWriter(modelFileName));
			bw.write(gson.toJson(getTopicVocabDist()));
			bw.close();
		}

	}

	protected void initVariables() {
		corpus = new ArrayList<LDADoc>();
		topics = new LDATopic[param.numTopics];
		alpha = new double[param.numTopics];
		phi = new double[param.numTopics][param.numVocab];
		for (int topic = 0; topic < param.numTopics; topic++) {
			topics[topic] = new LDATopic(param.numVocab);
		}

	}

	protected void copyModel(LDA LDAModel) {
		alpha = LDAModel.alpha.clone();
		phi = LDAModel.phi.clone();
	}

	protected static String format(double num) {
		return Fourmat.format(num);
	}

	static {
		randoms = new Randoms();
		gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
	}

	/**
	 * Initialize an LDA object for training
	 *
	 * @param parameters Parameters
	 */
	public LDA(LDAParam parameters) {
		this.type = TRAIN;
		this.param = parameters;
		initVariables();

		for (int topic = 0; topic < param.numTopics; topic++) {
			alpha[topic] = param.alpha;
		}
	}

	/**
	 * Initialize an LDA object for test using a pre-trained LDA object
	 *
	 * @param LDATrain   Pre-trained LDA object
	 * @param parameters Parameters
	 */
	public LDA(LDA LDATrain, LDAParam parameters) {
		this.type = TEST;
		this.param = parameters;
		initVariables();
		copyModel(LDATrain);
	}

	/**
	 * Initialize an LDA object for test using a pre-trained LDA model in file
	 *
	 * @param modelFileName Model file name
	 * @param parameters    Parameters
	 * @throws IOException IOException
	 */
	public LDA(String modelFileName, LDAParam parameters) throws IOException {
		LDA LDATrain = gson.fromJson(new FileReader(modelFileName), this.getClass());
		this.type = TEST;
		this.param = parameters;
		initVariables();
		copyModel(LDATrain);
	}

	public static void main(String args[]) throws IOException {
		LDAParam parameters = new LDAParam(LDACfg.sldaVocabFileName);

		LDA LDATrain = new LDA(parameters);
		LDATrain.readCorpus(LDACfg.sldaTrainCorpusFileName);
		if (parameters.constrained)
			LDATrain.readDocConstraints(LDACfg.sldaTrainConstraintsFileName);
		LDATrain.initialize();
		LDATrain.sample(LDACfg.numTrainIters);
		// LDATrain.writeModel(LDACfg.getModelFileName(modelName));

		LDA LDATest = new LDA(LDATrain, parameters);
		// LDA LDATest=new LDA(LDACfg.getModelFileName(modelName), parameters);
		LDATest.readCorpus(LDACfg.sldaTestCorpusFileName);
		if (parameters.constrained)
			LDATest.readDocConstraints(LDACfg.sldaTestConstraintsFileName);
		LDATest.initialize();
		LDATest.sample(LDACfg.numTestIters);
	}

	public void writeCoherence(String coherenceFileName) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(coherenceFileName, true));

		bw.newLine();
		bw.close();
	}
}