package eu.wdaqua.qanary.resolver;

class FoundEntity {

	private final String wikidataResource; 
	private String triplestoreId = null;
	private String surfaceForm; // language specific label of the location
	private final float score;
	private String annotatorComponent;
	private final String target; // part of the question string 
	private int key;
	private LocationType locationType;
	
	public FoundEntity(
			String wikidataResource,
			String target,
			float score) {
		this.wikidataResource = wikidataResource;
		this.target = target;
		this.score = score;
	}

	public void setAnnotator(String componentName) {
		this.annotatorComponent = componentName;
	}

	public void setTriplestoreId(String triplestoreId) {
		this.triplestoreId = triplestoreId;
	}

	public void setKey(int key) {
		this.key = key;
	}

	public void setLocationType(LocationType type) {
		this.locationType = type;
	}

	public void setSurfaceForm(String surfaceForm) {
		this.surfaceForm = surfaceForm;
	}

	public String getWikidataResource() {
		return this.wikidataResource;
	}

	public String getTriplestoreId() {
		return this.triplestoreId;
	}

	public String getSurfaceForm() {
		return this.surfaceForm;
	}

	public float getScore() {
		return this.score;
	}

	public String getAnnotator() {
		return this.annotatorComponent;
	}

	public String getQID() {
		return this.wikidataResource.substring(this.wikidataResource.indexOf("Q"));
	}

	public String getTargetString() {
		return this.target;
	}

	public int getKey() {
		return this.key;
	}

	public String getLocationType() {
		switch (this.locationType) {
			case STATE:
				return "dbr:States_of_Germany";
			case DISTRICT:
				return "dbr:Districts_of_Germany";
			default:
				return null;
		}
	}
}
