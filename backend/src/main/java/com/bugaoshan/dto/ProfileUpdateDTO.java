package com.bugaoshan.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

/** A partial update: omitted fields are preserved, while blank optional fields are cleared. */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProfileUpdateDTO {

    @Size(min = 2, max = 20, message = "昵称长度需在2-20之间")
    private String nickname;

    @Pattern(regexp = "1[3-9]\\d{9}", message = "手机号格式不正确")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱不能超过100个字符")
    private String email;

    @Size(max = 100, message = "院系不能超过100个字符")
    private String department;

    @JsonAlias("className")
    @Size(max = 100, message = "班级不能超过100个字符")
    private String className;

    @JsonAlias("studentNo")
    @Size(max = 20, message = "学号不能超过20个字符")
    private String studentNo;

    @Size(max = 50, message = "职称不能超过50个字符")
    private String title;

    @JsonAlias("officeLocation")
    @Size(max = 200, message = "办公室不能超过200个字符")
    private String officeLocation;

    @JsonIgnore
    private final Set<String> providedFields = new HashSet<>();

    public boolean hasField(String name) {
        return providedFields.contains(name);
    }

    @AssertTrue(message = "昵称不能为空")
    @JsonIgnore
    public boolean isNicknameValid() {
        return !hasField("nickname") || nickname != null;
    }

    public void setNickname(String value) {
        providedFields.add("nickname");
        nickname = normalizeOptional(value);
    }

    public void setPhone(String value) {
        providedFields.add("phone");
        phone = normalizeOptional(value);
    }

    public void setEmail(String value) {
        providedFields.add("email");
        email = normalizeOptional(value);
    }

    public void setDepartment(String value) {
        providedFields.add("department");
        department = normalizeOptional(value);
    }

    public void setClassName(String value) {
        providedFields.add("className");
        className = normalizeOptional(value);
    }

    public void setStudentNo(String value) {
        providedFields.add("studentNo");
        studentNo = normalizeOptional(value);
    }

    public void setTitle(String value) {
        providedFields.add("title");
        title = normalizeOptional(value);
    }

    public void setOfficeLocation(String value) {
        providedFields.add("officeLocation");
        officeLocation = normalizeOptional(value);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
