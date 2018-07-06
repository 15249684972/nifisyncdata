package bean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class KdmData implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Object code;
	private List<KdmData2> data=new ArrayList<KdmData2>();
	private Object state;
	public Object getCode() {
		return code;
	}
	public void setCode(Object code) {
		this.code = code;
	}
	public List<KdmData2> getData() {
		return data;
	}
	public void setData(List<KdmData2> data) {
		this.data = data;
	}
	public Object getState() {
		return state;
	}
	public void setState(Object state) {
		this.state = state;
	}
	
}
