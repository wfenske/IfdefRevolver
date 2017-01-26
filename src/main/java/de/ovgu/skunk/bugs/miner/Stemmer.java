package de.ovgu.skunk.bugs.miner;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashMap;

import net.didion.jwnl.JWNL;
import net.didion.jwnl.JWNLException;
import net.didion.jwnl.data.IndexWord;
import net.didion.jwnl.data.POS;
import net.didion.jwnl.dictionary.Dictionary;
import net.didion.jwnl.dictionary.MorphologicalProcessor;

public class Stemmer {
	private int MaxWordLength = 50;
	private static Dictionary dic;
	private static MorphologicalProcessor morph;
	private static boolean IsInitialized = false;
	public static HashMap<String, String> AllWords = null;

	/**
	 * establishes connection to the WordNet database
	 */
	public Stemmer() {
		AllWords = new HashMap<String, String>();

		try {
			JWNL.initialize(new FileInputStream("../resources/file_properties.xml"));
			dic = Dictionary.getInstance();
			morph = dic.getMorphologicalProcessor();
			// ((AbstractCachingDictionary)dic).
			// setCacheCapacity (10000);
			IsInitialized = true;
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Error initializing Stemmer: JWNLproperties.xml not found. ", e);
		} catch (JWNLException e) {
			throw new RuntimeException("Error initializing Stemmer.", e);
		}

	}

	public void Unload() {
		dic.close();
		Dictionary.uninstall();
		JWNL.shutdown();
	}

	/*
	 * stems a word with wordnet
	 * 
	 * @param word word to stem
	 * 
	 * @return the stemmed word or null if it was not found in WordNet
	 */
	public static String StemWordWithWordNet(String word) {
		if (!IsInitialized)
			return word;
		if (word == null)
			return null;
		if (morph == null)
			morph = dic.getMorphologicalProcessor();

		IndexWord w;
		try {
			w = morph.lookupBaseForm(POS.VERB, word);
			if (w != null)
				return w.getLemma().toString();
			w = morph.lookupBaseForm(POS.NOUN, word);
			if (w != null)
				return w.getLemma().toString();
			w = morph.lookupBaseForm(POS.ADJECTIVE, word);
			if (w != null)
				return w.getLemma().toString();
			w = morph.lookupBaseForm(POS.ADVERB, word);
			if (w != null)
				return w.getLemma().toString();
		} catch (JWNLException e) {
		}
		return null;
	}

	/**
	 * Stem a single word tries to look up the word in the AllWords HashMap If
	 * the word is not found it is stemmed with WordNet and put into AllWords
	 * 
	 * @param word
	 *            word to be stemmed
	 * @return stemmed word
	 */
	public static String Stem(String word) {
		// check if we already know the word
		String stemmedword = AllWords.get(word);
		if (stemmedword != null)
			return stemmedword; // return it if we already know it

		// don't check words with digits in them
		if (containsNumbers(word) == true)
			stemmedword = null;
		else // unknown word: try to stem it
			stemmedword = StemWordWithWordNet(word);

		if (stemmedword != null) {
			// word was recognized and stemmed with wordnet:
			// add it to hashmap and return the stemmed word
			AllWords.put(word, stemmedword);
			return stemmedword;
		}
		// word could not be stemmed by wordnet,
		// thus it is no correct english word
		// just add it to the list of known words so
		// we won't have to look it up again
		AllWords.put(word, word);
		return word;
	}

	public static boolean containsNumbers(String word) {

		return word.matches("\\d+");
	}

}
