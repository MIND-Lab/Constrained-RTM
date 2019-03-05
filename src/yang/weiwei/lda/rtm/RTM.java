package yang.weiwei.lda.rtm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

import yang.weiwei.util.MathUtil;
import yang.weiwei.lda.LDA;
import yang.weiwei.lda.LDACfg;
import yang.weiwei.lda.LDAParam;
import yang.weiwei.lda.util.LDATopic;
import yang.weiwei.util.IOUtil;
import cc.mallet.optimize.LimitedMemoryBFGS;
import com.google.gson.annotations.Expose;

/**
 * Relational topic model
 * 
 * @author Weiwei Yang
 *
 */
public class RTM extends LDA {
	public static final int TRAIN_GRAPH = 0;
	public static final int TEST_GRAPH = 1;

	@Expose
	protected double eta[];

	protected ArrayList<HashMap<Integer, Integer>> trainEdgeWeights;
	protected int numTrainEdges;

	protected ArrayList<HashMap<Integer, Integer>> testEdgeWeights;

	protected ArrayList<Integer[]> testEdgesLists;

	protected int numTestEdges;

	protected double weight[];

	protected double error;
	protected double PLR;
	protected double[] HITS;
	protected double[] NDCG;

	protected int atK = 10;

	protected double avgWeight;

	@Override
	public void initialize() throws IOException {
		// TODO Auto-generated method stub
		super.initialize();
		// optimize();
	}

	@Override
	public void initialize(String topicAssignFileName) throws IOException {
		// TODO Auto-generated method stub
		super.initialize(topicAssignFileName);
	}

	public void readCorpus(String corpusFileName) throws IOException {
		super.readCorpus(corpusFileName);
		for (int doc = 0; doc < numDocs; doc++) {
			trainEdgeWeights.add(new HashMap<Integer, Integer>());
			testEdgeWeights.add(new HashMap<Integer, Integer>());
		}
	}

	/**
	 * Read document links
	 * 
	 * @param graphFileName Graph file name
	 * @param graphType     Graph type
	 * @throws IOException IOException
	 */
	public void readGraph(String graphFileName, int graphType) throws IOException {
		System.out.println(graphFileName);
		System.out.println(graphType);

		BufferedReader br = new BufferedReader(new FileReader(graphFileName));
		String line, seg[];
		int u, v, w;
		while ((line = br.readLine()) != null) {
			seg = line.split("\t");

			/* the order is from right to left */
			v = Integer.valueOf(seg[0]);
			u = Integer.valueOf(seg[1]);

			w = (seg.length >= 3 ? Integer.valueOf(seg[2]) : 1);
			if (corpus.get(u).docLength() == 0 || corpus.get(v).docLength() == 0)
				continue;

			if (graphType == TRAIN_GRAPH) {
				trainEdgeWeights.get(u).put(v, w);
				numTrainEdges++;
				if (!param.directed) {
					trainEdgeWeights.get(v).put(u, w);
					numTrainEdges++;
				}
			}
			if (graphType == TEST_GRAPH && w > 0) {
				testEdgeWeights.get(u).put(v, w);
				numTestEdges++;
				if (!param.directed) {
					testEdgeWeights.get(v).put(u, w);
					numTestEdges++;
				}
			}
		}
		if (graphType == TRAIN_GRAPH && param.negEdge) {
			sampleNegEdge();
		}
		br.close();
	}

	public void readEdgeLists(String graphFileName) throws NumberFormatException, IOException {
		System.out.println(graphFileName);
		BufferedReader br = new BufferedReader(new FileReader(graphFileName));

		String line;
		String[] seg;
		Integer[] nodesList;
		while ((line = br.readLine()) != null) {
			seg = line.split("\t");

			nodesList = new Integer[seg.length];

			for (int i = 0; i < seg.length; i++)
				nodesList[i] = Integer.valueOf(seg[i]);
			testEdgesLists.add(nodesList);
		}
		br.close();
	}

	protected void sampleNegEdge() {
		int numNegEdges = (int) (numTrainEdges * param.negEdgeRatio), u, v;
		for (int i = 0; i < numNegEdges; i++) {
			u = randoms.nextInt(numDocs);
			v = randoms.nextInt(numDocs);
			while (u == v || corpus.get(u).docLength() == 0 || corpus.get(v).docLength() == 0
					|| trainEdgeWeights.get(u).containsKey(v)) {
				u = randoms.nextInt(numDocs);
				v = randoms.nextInt(numDocs);
			}
			trainEdgeWeights.get(u).put(v, 0);
			numTrainEdges++;
		}
	}

	protected void printParam() {
		super.printParam();
		param.printRTMParam("\t");
		IOUtil.println("\t#train edges: " + numTrainEdges);
		if (param.negEdge)
			IOUtil.println("\t#neg edges: " + (int) (numTrainEdges * param.negEdgeRatio));
	}

	protected void printMetrics() {
		super.printMetrics();
		IOUtil.println("Predictive Link Rank: " + format(PLR));
	}

	public void sample(int numIters, int burnin) throws IOException {
		if (param.metricsFileName.length() > 0) {
			BufferedWriter bw = new BufferedWriter(new FileWriter(param.metricsFileName));
			bw.write("logLikelihood;perplexity");
			bw.newLine();
			for (int iteration = 1; iteration <= numIters; iteration++) {
				// long startTime = System.nanoTime();
				for (int doc = 0; doc < numDocs; doc++) {
					weight = new double[trainEdgeWeights.get(doc).size()];
					sampleDoc(doc);
					// System.out.print(doc + " ");

				}
				for (int i = 0; i < param.numTopics; i++) {
					LDATopic t = topics[i];
					// System.out.println(t.getTotalTokens());
				}
				// long endTime = System.nanoTime();
				// System.out.println("sample docs" + (endTime-startTime));
				computeLogLikelihood();
				perplexity = Math.exp(-logLikelihood / numTestWords);
				if (type == TRAIN) {
					optimize();
				}

				if (param.verbose && iteration % 100 == 0) {
					IOUtil.println("<" + iteration + ">" + "\tLog-LLD: " + format(logLikelihood) + "\tPPX: "
							+ format(perplexity));
				}
				/*
				 * if (param.verbose && numTestEdges > 0) { computeAvgWeight();
				 * IOUtil.print("\tAvg Weight: " + format(avgWeight) + "\n"); }
				 * 
				 * if (param.verbose && numTestEdges > 0) { computeError();
				 * IOUtil.print("\tError: " + format(error)); }
				 * 
				 * if (iteration % param.showPLRInterval == 0 || iteration == numIters)
				 * computeLinkPrediction(); if (param.verbose && numTestEdges > 0) {
				 * IOUtil.print("\tPLR: " + format(PLR)); }
				 */

				if (param.verbose)
					// IOUtil.println();
					if (param.updateAlpha && iteration % param.updateAlphaInterval == 0 && type == TRAIN) {
						updateHyperParam();
					}
				if (burnin > 0)
					burnin--;
				else {
					bw.write(logLikelihood + ";" + perplexity);
					bw.newLine();
				}
			}
			bw.close();

			if (type != TRAIN)
				computeLinkPrediction();

			System.out.println("********************computo phi********************");

			computePhi();
			printMetrics();
		} else {
			for (int iteration = 1; iteration <= numIters; iteration++) {
				for (int doc = 0; doc < numDocs; doc++) {
					weight = new double[trainEdgeWeights.get(doc).size()];
					sampleDoc(doc);
				}

				computeLogLikelihood();
				perplexity = Math.exp(-logLikelihood / numTestWords);
				if (type == TRAIN) {
					optimize();
				}
				if (param.verbose && iteration % 500 == 0) {
					IOUtil.println("<" + iteration + ">" + "\tLog-LLD: " + format(logLikelihood) + "\tPPX: "
							+ format(perplexity));
				}
				/*
				 * if (param.verbose && numTestEdges > 0) { computeAvgWeight();
				 * IOUtil.print("\tAvg Weight: " + format(avgWeight) + "\n"); } if
				 * (param.verbose && numTestEdges > 0) { computeError();
				 * IOUtil.print("\tError: " + format(error)); }
				 * 
				 * if (iteration % param.showPLRInterval == 0 || iteration == numIters)
				 * computeLinkPrediction(); if (param.verbose && numTestEdges > 0) {
				 * IOUtil.print("\tPLR: " + format(PLR)); }
				 */
				// if (param.verbose)
				// IOUtil.println();
				if (param.updateAlpha && iteration % param.updateAlphaInterval == 0 && type == TRAIN) {
					updateHyperParam();
				}

				if (burnin > 0)
					burnin--;
			}

			if (type != TRAIN)
				computeLinkPrediction();

			System.out.println("********************computo phi********************");
			computePhi();
			printMetrics();
		}
		if (type == TRAIN) {
			for (int topic = 0; topic < param.numTopics; topic++) {
				IOUtil.println(topWordsByFreq(topic, 10));
			}
		}
	}

	public void sample(int numIters) throws IOException {
		sample(numIters, 0);
	}

	protected void sampleDoc(int doc) {
		int oldTopic, newTopic, i = 0;
		for (int d : trainEdgeWeights.get(doc).keySet()) {
			weight[i] = computeWeight(doc, d);
			i++;
		}

		int interval = getSampleInterval();
		for (int token = 0; token < corpus.get(doc).docLength(); token += interval) {
			oldTopic = unassignTopic(doc, token);
			i = 0;
			for (int d : trainEdgeWeights.get(doc).keySet()) {
				weight[i] -= eta[oldTopic] / corpus.get(doc).docLength() * corpus.get(d).getTopicCount(oldTopic)
						/ corpus.get(d).docLength();
				i++;
			}
			newTopic = sampleTopic(doc, token, oldTopic);
			assignTopic(doc, token, newTopic);
			i = 0;
			for (int d : trainEdgeWeights.get(doc).keySet()) {
				weight[i] += eta[newTopic] / corpus.get(doc).docLength() * corpus.get(d).getTopicCount(newTopic)
						/ corpus.get(d).docLength();
				i++;
			}
		}
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

	protected double topicUpdating(int doc, int topic, int vocab) {
		double score = super.topicUpdating(doc, topic, vocab);
		int i = 0;
		double temp;
		for (int d : trainEdgeWeights.get(doc).keySet()) {
			temp = MathUtil.sigmoid(weight[i] + eta[topic] / corpus.get(doc).docLength()
					* corpus.get(d).getTopicCount(topic) / corpus.get(d).docLength());
			score += Math.log(trainEdgeWeights.get(doc).get(d) > 0 ? temp : 1.0 - temp);
			i++;
		}

		return score;
	}

	protected void optimize() {
		// long startTime = System.nanoTime();
		RTMFunction optimizable = new RTMFunction(this);
		LimitedMemoryBFGS lbfgs = new LimitedMemoryBFGS(optimizable);
		try {
			lbfgs.optimize();
		} catch (Exception e) {
			return;
		}
		for (int topic = 0; topic < param.numTopics; topic++) {
			eta[topic] = optimizable.getParameter(topic);
		}
		// long endTime = System.nanoTime();
		// String diff = millisToShortDHMS(endTime - startTime);
		// System.out.println("ottimizzazione: " + (endTime - startTime));
	}

	protected double computeWeight(int doc1, int doc2) {
		double weight = 0.0;
		for (int topic = 0; topic < param.numTopics; topic++) {
			weight += eta[topic] * corpus.get(doc1).getTopicCount(topic) / corpus.get(doc1).docLength()
					* corpus.get(doc2).getTopicCount(topic) / corpus.get(doc2).docLength();
		}
		return weight;
	}

	protected double computeEdgeProb(int doc1, int doc2) {
		return MathUtil.sigmoid((computeWeight(doc1, doc2)));
	}

	protected void computeError() {
		error = 0.0;
		if (numTestEdges == 0)
			return;
		for (int doc = 0; doc < numDocs; doc++) {
			for (int d : testEdgeWeights.get(doc).keySet()) {
				error += 1.0 - computeEdgeProb(doc, d);
			}
		}
		error /= (double) numTestEdges;
	}

	protected void computeLinkPrediction() {
		computePLR();
		HITS = new double[atK];
		NDCG = new double[atK];

		double[][] ndcg_temp = new double[testEdgesLists.size()][atK];
		double[][] hits_temp = new double[testEdgesLists.size()][atK];

		for (int j = 0; j < testEdgesLists.size(); j++) {
			Integer[] edge = testEdgesLists.get(j);
			int u = edge[0];
			int v = edge[1];

			ArrayList<RTMDocProb> docProbs = new ArrayList<RTMDocProb>();

			for (int i : edge) {
				if (u == i)
					continue;
				docProbs.add(new RTMDocProb(i, computeEdgeProb(u, i)));
			}
			// sort probabilities
			Collections.sort(docProbs);
			// compute metrics at k
			for (int k = 1; k < atK+1; k++) {

				hits_temp[j][k-1] = 0.0;
				ndcg_temp[j][k-1] = 0.0;

				for (int l = 0; l < k; l++) {
					int item = docProbs.get(l).getDocNo();
					if (item == v) {
						// if v is in the k-list return 1
						hits_temp[j][k-1] = 1.0;
						ndcg_temp[j][k-1] = Math.log(2.0) / Math.log((double) l + 2.0);
					}
				}
			}
		}

		for (int k = 1; k < atK+1; k++) {
			for (int j = 0; j < testEdgesLists.size(); j++) {

				HITS[k - 1] += hits_temp[j][k - 1];
				NDCG[k - 1] += ndcg_temp[j][k - 1];
			}
			HITS[k-1] = HITS[k-1] / testEdgesLists.size();
			NDCG[k-1] = NDCG[k-1] / testEdgesLists.size();
		}
	}

	private void computePLR() {
		PLR = 0.0;
		if (numTestEdges == 0)
			return;
		ArrayList<RTMDocProb> docProbs = new ArrayList<RTMDocProb>();
		for (int doc = 0; doc < numDocs; doc++) {
			if (testEdgeWeights.get(doc).size() == 0)
				continue;
			docProbs.clear();
			for (int d = 0; d < numDocs; d++) {
				if (d == doc)
					continue;
				docProbs.add(new RTMDocProb(d, computeEdgeProb(doc, d)));
			}
			Collections.sort(docProbs);
			for (int i = 0; i < docProbs.size(); i++) {
				if (testEdgeWeights.get(doc).containsKey(docProbs.get(i).getDocNo())) {
					PLR += i + 1;
				}
			}
		}
		PLR /= (double) numTestEdges;
	}

	protected void computeAvgWeight() {
		avgWeight = 0.0;
		if (numTestEdges == 0)
			return;
		for (int doc = 0; doc < numDocs; doc++) {
			for (int d : testEdgeWeights.get(doc).keySet()) {
				avgWeight += computeWeight(doc, d);
			}
		}
		avgWeight /= (double) numTestEdges;
	}

	/**
	 * Write predictive link rank to file
	 * 
	 * @param plrFileName PLR file name
	 * @throws IOException IOException
	 */
	public void writePLR(String plrFileName) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(plrFileName));
		ArrayList<RTMDocProb> docProbs = new ArrayList<RTMDocProb>();
		for (int doc = 0; doc < numDocs; doc++) {
			if (testEdgeWeights.get(doc).size() == 0)
				continue;
			docProbs.clear();
			for (int d = 0; d < numDocs; d++) {
				if (d == doc)
					continue;
				docProbs.add(new RTMDocProb(d, computeEdgeProb(doc, d)));
			}
			Collections.sort(docProbs);
			for (int i = 0; i < docProbs.size(); i++) {
				if (testEdgeWeights.get(doc).containsKey(docProbs.get(i).getDocNo())) {
					bw.write(doc + "\t" + docProbs.get(i).getDocNo() + "\t" + (i + 1));
					bw.newLine();
				}
			}
		}
		bw.close();
	}

	/**
	 * Write predicted document link probabilities to file
	 * 
	 * @param predFileName Prediction file name
	 * @throws IOException IOException
	 */
	public void writePred(String predFileName) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(predFileName));
		for (int doc1 = 0; doc1 < numDocs; doc1++) {
			for (int doc2 = doc1 + 1; doc2 < numDocs; doc2++) {
				double prob = computeEdgeProb(doc1, doc2);
				bw.write(prob + " ");
			}
			bw.newLine();
		}
		bw.close();
	}

	/**
	 * Write regression values to file
	 * 
	 * @param regFileName Regression value file name
	 * @throws IOException IOException
	 */
	public void writeRegValues(String regFileName) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(regFileName));
		for (int doc1 = 0; doc1 < numDocs; doc1++) {
			for (int doc2 = 0; doc2 < numDocs; doc2++) {
				double reg = computeWeight(doc1, doc2);
				bw.write(reg + " ");
			}
			bw.newLine();
		}
		bw.close();
	}

	public void writePlrValues(String plrFileName) throws IOException {
		String folders = new File(plrFileName).getParent();
		if (!new File(folders).exists())
			new File(folders).mkdirs();
		BufferedWriter bw = new BufferedWriter(new FileWriter(plrFileName, true));
		bw.write(getPLR() + "\n");
		bw.close();
	}

	protected void getNumTestWords() {
		numTestWords = numWords;
	}

	protected int getStartPos() {
		return 0;
	}

	protected int getSampleSize(int docLength) {
		return docLength;
	}

	protected int getSampleInterval() {
		return 1;
	}

	/**
	 * Get predictive link rank
	 * 
	 * @return PLR
	 */
	public double getPLR() {
		return PLR;
	}

	/**
	 * Get the weight of a topic
	 * 
	 * @param topic Topic
	 * @return The weight of given topic
	 */
	public double getTopicWeight(int topic) {
		return eta[topic];
	}

	/**
	 * Get topic weights
	 * 
	 * @return Topic weights
	 */
	public double[] getTopicWeights() {
		return eta.clone();
	}

	public Set<Integer> getTrainLinkedDocs(int doc) {
		return trainEdgeWeights.get(doc).keySet();
	}

	public int getTrainEdgeWeight(int doc1, int doc2) {
		return trainEdgeWeights.get(doc1).get(doc2);
	}

	protected void initVariables() {
		super.initVariables();
		trainEdgeWeights = new ArrayList<HashMap<Integer, Integer>>();
		testEdgeWeights = new ArrayList<HashMap<Integer, Integer>>();
		testEdgesLists = new ArrayList<Integer[]>();
		eta = new double[param.numTopics];
		// optimize();
	}

	protected void copyModel(LDA LDAModel) {
		super.copyModel(LDAModel);
		eta = ((RTM) LDAModel).eta.clone();
	}

	/**
	 * Initialize an RTM object for training
	 * 
	 * @param parameters Parameters
	 */
	public RTM(LDAParam parameters) {
		super(parameters);
	}

	/**
	 * Initialize an RTM object for test using a pre-trained RTM object
	 * 
	 * @param RTMTrain   Pre-trained RTM object
	 * @param parameters Parameters
	 */
	public RTM(RTM RTMTrain, LDAParam parameters) {
		super(RTMTrain, parameters);
	}

	/**
	 * Initialize an RTM object for test using a pre-trained RTM model in file
	 * 
	 * @param modelFileName Model file name
	 * @param parameters    Parameters
	 * @throws IOException IOException
	 */
	public RTM(String modelFileName, LDAParam parameters) throws IOException {
		super(modelFileName, parameters);
	}

	public static void main(String args[]) throws IOException {
		LDAParam parameters = new LDAParam(LDACfg.rtmVocabFileName);

		RTM RTMTrain = new RTM(parameters);
		RTMTrain.readCorpus(LDACfg.rtmTrainCorpusFileName);
		RTMTrain.readGraph(LDACfg.rtmTrainLinkFileName, TRAIN_GRAPH);
		RTMTrain.readGraph(LDACfg.rtmTrainLinkFileName, TEST_GRAPH);
		RTMTrain.initialize();
		RTMTrain.sample(LDACfg.numTrainIters);
		// RTMTrain.writeModel(LDACfg.getModelFileName(modelName));

		RTM RTMTest = new RTM(RTMTrain, parameters);
		// RTM RTMTest=new RTM(LDACfg.getModelFileName(modelName), parameters);
		RTMTest.readCorpus(LDACfg.rtmTestCorpusFileName);
		RTMTest.readGraph(LDACfg.rtmTestLinkFileName, TEST_GRAPH);
		RTMTest.initialize();
		RTMTest.sample(LDACfg.numTestIters);
		// RTMTest.writePred(LDACfg.rtmPredLinkFileName);
	}

	public void writeHitsValues(String hitsFileName) throws IOException {
		String folders = new File(hitsFileName).getParent();
		if (!new File(folders).exists())
			new File(folders).mkdirs();
		BufferedWriter bw = new BufferedWriter(new FileWriter(hitsFileName, true));
		for (int k = 0; k < atK; k++) {
			if (k == atK - 1)
				bw.write(HITS[k] + "\n");
			else
				bw.write(HITS[k] + ";");
		}
		bw.close();

	}

	public void writeNdcgValues(String ndcgFileName) throws IOException {
		String folders = new File(ndcgFileName).getParent();
		if (!new File(folders).exists())
			new File(folders).mkdirs();
		BufferedWriter bw = new BufferedWriter(new FileWriter(ndcgFileName, true));
		for (int k = 0; k < atK; k++) {
			if (k == atK - 1)
				bw.write(NDCG[k] + "\n");
			else
				bw.write(NDCG[k] + ";");
		}
		bw.close();

	}
}
