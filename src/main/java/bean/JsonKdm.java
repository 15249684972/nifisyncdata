package bean;

import java.io.Serializable;
import java.util.HashMap;

public class JsonKdm extends HashMap<String,HashMap<String, KdmData2>> implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Object code;
	private HashMap<Object, KdmData2> data;
	public Object getCode() {
		return code;
	}
	public void setCode(Object code) {
		this.code = code;
	}
	public HashMap<Object, KdmData2> getData() {
		return data;
	}
	public void setData(HashMap<Object, KdmData2> data) {
		this.data = data;
	}
}
