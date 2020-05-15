/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
import com.intuit.karate.KarateOptions;
import com.intuit.karate.junit4.Karate;
import org.junit.runner.RunWith;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RunWith(Karate.class)
// use KarateOptions to limit test to execute
@KarateOptions(
        //features = { "classpath:txns" }, 
        tags = "~@ignore")
public class RunnerIT extends AbstactIT {

}
