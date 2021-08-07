package eu.wdaqua.qanary.resolver;

class FoundEntity {

	private final String wikidataResource;
	private String triplestoreId = null;
	private final String surfaceForm;
	private final float score;
	private String annotatorComponent;
	private String target;
	private int districtKey;
	
	public FoundEntity(
			String wikidataResource,
			String surfaceForm,
			float score) {
		this.wikidataResource = wikidataResource;
		this.surfaceForm = surfaceForm;
		this.score = score;
	}

	public void setAnnotator(String componentName) {
		this.annotatorComponent = componentName;
	}

	public void setTriplestoreId(String triplestoreId) {
		this.triplestoreId = triplestoreId;
	}

	public void setTargetString(String target) {
		this.target = target;
	}

	public void setDistrictKey(int key) {
		this.districtKey = key;
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

	public int getDistrictKey() {
		return this.districtKey;
	}

}
