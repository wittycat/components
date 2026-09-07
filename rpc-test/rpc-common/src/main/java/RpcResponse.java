import java.io.Serializable;

/**
 * Created by chenxun.
 * Date: 2026/6/28 11:34
 * Description:
 */
public class RpcResponse implements Serializable {

    private static final long serialVersionUID = -3126225061156652310L;

    /**
     * 成功结果
     */
    private Object result;
    /**
     * 异常信息
     */
    private Exception exception;

    /**
     * getter/setter/构造器
     * @return
     */
    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public Exception getException() {
        return exception;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }
}