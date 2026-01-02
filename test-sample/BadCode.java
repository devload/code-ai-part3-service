package com.example;

/**
 * 의도적으로 문제가 있는 코드 - 테스트용
 */
public class BadCode {

    // 하드코딩된 비밀번호 (보안 문제)
    private String password = "admin123";
    private String apiKey = "sk-1234567890abcdef";

    // 매직 넘버
    private static final int x = 86400;

    // 너무 많은 파라미터
    public void processData(String a, int b, double c, String d,
                           boolean e, List<String> f, Map<String, Object> g, int h) {
        // 너무 긴 메서드
        if (a != null) {
            if (b > 0) {
                if (c > 0.0) {
                    if (d != null) {
                        if (e) {
                            System.out.println("Deep nesting!");
                        }
                    }
                }
            }
        }

        // null 체크 → Optional로 변환 가능
        if (a != null) {
            a.toLowerCase();
        }

        // 불필요한 if-else
        if (b > 10) {
            return true;
        } else {
            return false;
        }

        // SQL Injection 위험
        String query = "SELECT * FROM users WHERE id = '" + userId + "'";

        // for-if 패턴 → Stream으로 변환 가능
        for (String item : items) {
            if (item.startsWith("A")) {
                System.out.println(item);
            }
        }

        // 문자열 연결 → String.format으로 변환
        String msg = "User " + name + " logged in";
    }

    // 클래스명 명명 규칙 위반
    class badInnerClass {
        int Value;  // 필드명 대문자로 시작
    }
}
