package framework.utils;

public class ApiResponse {
    private String status;
    private int code;
    private Object data;

    public ApiResponse(String status, int code, Object data) {
        this.status = status;
        this.code = code;
        this.data = data;
    }

    public String getStatus() { return status; }
    public int getCode() { return code; }
    public Object getData() { return data; }

    public void setStatus(String status) { this.status = status; }
    public void setCode(int code) { this.code = code; }
    public void setData(Object data) { this.data = data; }
}
