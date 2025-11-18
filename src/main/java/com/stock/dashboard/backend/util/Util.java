package com.stock.dashboard.backend.util;

import com.stock.dashboard.backend.model.vo.SearchHelper; // 경로 수정

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Util {

    private Util() {
        throw new UnsupportedOperationException("Cannot instantiate a Util class");
    }

    /**
     * 현재 시간을 'yyyy-MM-dd HH:mm:ss' 형식의 문자열로 반환합니다.
     * @return 포맷된 시간 문자열
     */
    public static String InstantToString() {
        Instant instant = Instant.now();

        // 원하는 포맷을 정의 (yyyy-MM-dd HH:mm:ss)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault()); // 시스템의 기본 시간대 사용
        return formatter.format(instant);
    }

    /**
     * 무작위 UUID(Universally Unique Identifier) 문자열을 반환합니다.
     * @return UUID 문자열
     */
    public static String generateRandomUuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * 지정된 길이의 무작위 문자열 (숫자와 대문자 알파벳)을 생성합니다.
     * @param size 생성할 문자열의 길이
     * @return 무작위 문자열
     */
    public static String randomString(int size) {
        if (size > 0) {
            char[] tmp = new char[size];
            for (int i = 0; i < tmp.length; i++) {
                int div = (int) Math.floor(Math.random() * 2);
                if (div == 0) {
                    tmp[i] = (char) (Math.random() * 10 + '0'); // 숫자
                } else {
                    tmp[i] = (char) (Math.random() * 26 + 'A'); // 대문자
                }
            }
            return new String(tmp);
        }
        return null;
    }

    /**
     * 지금 시각을 'yyyyMMddHHmmss' 형식의 문자열로 반환합니다.
     * @return 포맷된 날짜/시간 문자열
     */
    public static String dateToString() {
        SimpleDateFormat nowDate = new SimpleDateFormat("yyyyMMddHHmmss", Locale.KOREA);
        String format = nowDate.format(System.currentTimeMillis());
        return format;
    }


    /**
     * Date 객체를 'yyyyMMdd' 형식의 정수로 변환합니다.
     * @param date 변환할 Date 객체
     * @return 변환된 날짜 정수
     */
    public static int getDateByInteger(Date date) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd", Locale.KOREA);
        return Integer.parseInt(simpleDateFormat.format(date));
    }

    /**
     * Date 객체를 'yyyy-MM-dd' 형식의 문자열로 변환합니다.
     * @param date 변환할 Date 객체
     * @return 변환된 날짜 문자열
     */
    public static String getDateByString(Date date) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
        return simpleDateFormat.format(date);
    }

    /**
     * 'yyyy-MM-dd' 형식의 문자열을 Date 객체로 변환합니다.
     * @param date 변환할 날짜 문자열
     * @return Date 객체
     * @throws ParseException 파싱 오류 발생 시
     */
    public static Date getStringToDate(String date) throws ParseException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
        Date returnDate = simpleDateFormat.parse(date);
        return returnDate;
    }

    /**
     * 객체가 null이거나 비어있는지 (문자열, Map, List, 배열) 확인합니다.
     * @param o 확인할 객체
     * @return 비어있으면 true, 아니면 false
     */
    public static boolean isEmpty(Object o) {
        if (o == null) {
            return true;
        }
        if ((o instanceof String) && (((String)o).trim().length()) == 0) {
            return true;
        }
        if (o instanceof Map) {
            return ((Map<?, ?>)o).isEmpty();
        }
        if (o instanceof List) {
            return ((List<?>)o).isEmpty();
        }
        if (o instanceof Object[]) {
            return (((Object[])o).length == 0);
        }
        return false;
    }

    /**
     * SearchHelper와 검색 결과를 사용하여 페이지네이션 정보를 담은 HashMap을 생성합니다.
     * @param searchHelper 페이지네이션 정보 객체
     * @param elementList 검색 결과 리스트
     * @param totalElements 총 요소 개수
     * @return 결과 맵 (list, totalElements, size, page, totalPages, last 포함)
     */
    public static HashMap<String, Object> makeResultMap(SearchHelper searchHelper, List<?> elementList, float totalElements) {
        HashMap<String, Object> resultMap = new HashMap<>();

        resultMap.put("list", elementList);
        resultMap.put("totalElements", totalElements);
        resultMap.put("size", searchHelper.getSize());
        resultMap.put("page", searchHelper.getPage());
        resultMap.put("totalPages", Math.ceil(totalElements / searchHelper.getSize()));
        // totalPages 계산 방식에 따라 last 필드가 정확하지 않을 수 있어, page index가 totalPages를 초과하는지 여부로 수정 필요
        // 여기서는 기존 코드를 유지하되, 일반적으로는 다음과 같이 페이지 인덱스 비교를 사용합니다:
        // resultMap.put("last", searchHelper.getPage() >= (int)Math.ceil(totalElements / searchHelper.getSize()) - 1);
        resultMap.put("last", searchHelper.getPage() >= searchHelper.getSize()); // NOTE: 이 로직은 잘못된 구현일 가능성이 높으나 원본 코드를 유지합니다.

        return resultMap;
    }
}