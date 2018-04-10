package yang.weiwei.lda.util;

import java.util.ArrayList;
import java.util.HashMap;

public class CLDADoc extends LDADoc{
		//each document is assigned a list of must-constraint docs and cannot-constraint docs.
		public ArrayList<Integer> mustLink; 
		public ArrayList<Integer> cannotLink; 
		
		public CLDADoc(int numTopics, int numVocab) {
			super(numTopics, numVocab);
			mustLink = new ArrayList<Integer>();
			cannotLink = new ArrayList<Integer>();

		}

		public CLDADoc(String document, int numTopics, int numVocab) {
			super(document, numTopics, numVocab);
			mustLink = new ArrayList<Integer>();
			cannotLink = new ArrayList<Integer>();

		}
		
		public CLDADoc(String document, int numTopics, HashMap<String, Integer> vocabMap) {
			super(document, numTopics, vocabMap);
			mustLink = new ArrayList<Integer>();
			cannotLink = new ArrayList<Integer>();
		}

		public void addMustLink(int doc) {
			mustLink.add(doc);
		}

		public void addCannotLink(int doc) {
			cannotLink.add(doc);
		}
	}

