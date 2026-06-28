package test02;

import java.io.Serializable;

/**
 * Created by chenxun.
 * Date: 2026/6/28 16:24
 * Description:
 */
public class User  implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private int age;
    private String message;

    public User(String name, int age, String message) {
        this.name = name;
        this.age = age;
        this.message = message;
    }

    // Getter / Setter 省略（实际需要）
    public String getName() { return name; }
    public int getAge() { return age; }
    public String getMessage() { return message; }

    @Override
    public String toString() {
        return String.format("User{name='%s', age=%d, message='%s'}", name, age, message);
    }

}