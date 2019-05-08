package xin.common.qqwry;

import lombok.Data;
import lombok.ToString;


@ToString(of = {"region","operator"})
@Data
public class Location {
    private String region;
    private String operator;
}

