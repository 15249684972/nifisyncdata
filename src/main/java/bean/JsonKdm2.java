package bean;

import java.io.Serializable;
import java.util.HashMap;

public class JsonKdm2 extends HashMap<Object, Object> implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Object time;
	private Object value;
	public Object getTime() {
		return time;
	}
	public void setTime(Object time) {
		this.time = time;
	}
	public Object getValue() {
		return value;
	}
	public void setValue(Object value) {
		this.value = value;
	}
	
	
}
