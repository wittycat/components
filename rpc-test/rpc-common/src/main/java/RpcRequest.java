import java.io.Serializable;

/**
 * Created by chenxun.
 * Date: 2026/6/28 11:34
 * Description:
 */
public class RpcRequest implements Serializable {

    private static final long serialVersionUID = -5386037218930787974L;

    /**
     * 接口全类名
     */
    private String className;
    /*
     * 调用方法名
     */
    private String methodName;
    /**
     *  参数类型
     */
    private Class<?>[] paramTypes;
    /**
     * 参数值
     */
    private Object[] params;

    /**
     * getter/setter/构造器
     * @return
     */
    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Class<?>[] getParamTypes() {
        return paramTypes;
    }

    public void setParamTypes(Class<?>[] paramTypes) {
        this.paramTypes = paramTypes;
    }

    public Object[] getParams() {
        return params;
    }

    public void setParams(Object[] params) {
        this.params = params;
    }
}