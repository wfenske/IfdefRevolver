package de.ovgu.skunk.bugs.eval.data;


public class CommitFile implements Comparable<CommitFile> {

	public String Filename;
	public boolean smelly;
	
	/**
	 * Instantiates a new changedFile.
	 *
	 * @param name the name
	 */
	public CommitFile(String name, boolean smelly)
	{
		this.Filename = name;
		this.smelly = smelly;
	}
	
	public String getFile(){
		return this.Filename;
	}
	
	public boolean getSmelly(){
		return this.smelly;
	}
	
	public void setSmelly(boolean smelly){
		this.smelly = smelly;
	}
	
	@Override
	public int compareTo(CommitFile obj) {	
		return obj.getFile().compareTo(this.getFile());		
	}
}
