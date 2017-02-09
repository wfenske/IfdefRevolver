package de.ovgu.skunk.bugs.minecommits;
import org.repodriller.domain.Commit;
import org.repodriller.persistence.PersistenceMechanism;
import org.repodriller.scm.CommitVisitor;
import org.repodriller.scm.SCMRepository;

import java.util.HashMap;
import java.util.Map;

public class WordCountVisitor implements CommitVisitor {
	
	private Map<String, Integer> words;
	private Stemmer stemmer;
	
	public WordCountVisitor(){
		words = new HashMap<String, Integer>();
		stemmer = new Stemmer();
	}
	
	@Override
	public void process(SCMRepository repo, Commit commit, PersistenceMechanism writer) {
		
		String message = commit.getMsg();
		message = message.replaceAll("[^a-zA-Z0-9\\s]", "");
		message = message.toLowerCase();
		
		String[] msg_words = message.split(" ");
		
		for(int i=0; i < msg_words.length; i++){
			String current_word = msg_words[i];
			// TODO: Wortstamm holen
			
			if(!words.containsKey(current_word)) words.put(current_word, 0);
			
			int currentCount = words.get(current_word);
			words.put(current_word, currentCount + 1);
		}
		
	
	}

	public void writeWordResults(PersistenceMechanism writer) {
		//Map durchgehen
		for(Map.Entry<String, Integer> entry: words.entrySet()){
			writer.write(
					entry.getKey(),
					entry.getValue()
				);
		}
		
		
	}
	
	@Override
	public String name() {
		return "word count";
	}
}
