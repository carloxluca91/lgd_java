package steps.abstractstep;

import org.apache.log4j.Logger;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.api.java.UDF3;
import org.apache.spark.sql.api.java.UDF4;
import org.apache.spark.sql.api.java.UDF6;
import org.apache.spark.sql.types.DataTypes;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

public abstract class AbstractStep extends StepUtils {

    protected Logger logger;
    protected static SparkSession sparkSession;
    private static Properties configProperties;

    // input and output dirs for a step
    protected String stepInputDir;
    protected String stepOutputDir;

    protected String dataDaPattern;
    protected String dataAPattern;

    protected AbstractStep(String loggerName){

        logger = Logger.getLogger(loggerName);

        try{

            configProperties = new Properties();
            configProperties.load(getClass().getClassLoader().getResourceAsStream("./lgd.properties"));

            dataDaPattern = getLGDPropertyValue("params.datada.pattern");
            dataAPattern = getLGDPropertyValue("params.dataa.pattern");
            logger.debug("dataDaPattern: " + dataDaPattern);
            logger.debug("dataAPattern: " + dataAPattern);

            getSparkSessionWithUDFs();
        }
        catch (IOException ex){

            logger.error(ex.getMessage());
        }
    }

    public String getLGDPropertyValue(String key) {
        return configProperties.getProperty(key);
    }

    private void getSparkSessionWithUDFs(){

        sparkSession = new SparkSession.Builder()
                .appName("LGDApp")
                .master("local")
                .getOrCreate();

        registerUDFsForDateComparison();
        registerUDFsForDateManipulation();

    }

    private void registerUDFsForDateComparison(){

        // UDFS THAT COMPARE DATES AND RETURN BOOLEAN COLUMN

        // returns true if date1 (with pattern date1Pattern) > date 2 (with pattern date2Pattern)
        UDF4<String, String, String, String, Boolean> date1GreaterThanDate2Udf = (UDF4<String, String, String, String, Boolean>)
                (date1, date1Pattern, date2, date2Pattern) -> {

                    LocalDate localDate1 = LocalDate.parse(date1, DateTimeFormatter.ofPattern(date1Pattern));
                    LocalDate localDate2 = LocalDate.parse(date2, DateTimeFormatter.ofPattern(date2Pattern));
                    return localDate1.compareTo(localDate2) > 0;
                };

        sparkSession.udf().register("date1GtDate2", date1GreaterThanDate2Udf, DataTypes.BooleanType);

        // returns true if date1 (with pattern date1Pattern) >= date 2 (with pattern date2Pattern)
        UDF4<String, String, String, String, Boolean> date1GreaterOrEqThanDate2Udf = (UDF4<String, String, String, String, Boolean>)
                (date1, date1Pattern, date2, date2Pattern) -> {

                    LocalDate localDate1 = LocalDate.parse(date1, DateTimeFormatter.ofPattern(date1Pattern));
                    LocalDate localDate2 = LocalDate.parse(date2, DateTimeFormatter.ofPattern(date2Pattern));
                    return localDate1.compareTo(localDate2) >= 0;
                };

        sparkSession.udf().register("date1GeqDate2", date1GreaterOrEqThanDate2Udf, DataTypes.BooleanType);

        // returns true if date1 (with pattern date1Pattern) < date 2 (with pattern date2Pattern)
        UDF4<String, String, String, String, Boolean> date1LessThanDate2Udf = (UDF4<String, String, String, String, Boolean>)
                (date1, date1Pattern, date2, date2Pattern) -> {

                    LocalDate localDate1 = LocalDate.parse(date1, DateTimeFormatter.ofPattern(date1Pattern));
                    LocalDate localDate2 = LocalDate.parse(date2, DateTimeFormatter.ofPattern(date2Pattern));
                    return localDate1.compareTo(localDate2) < 0;
                };

        sparkSession.udf().register("date1LtDate2", date1LessThanDate2Udf, DataTypes.BooleanType);

        // returns true if date1 (with pattern date1Pattern) <= date 2 (with pattern date2Pattern)
        UDF4<String, String, String, String, Boolean> date1LessOrEqualThanDate2Udf = (UDF4<String, String, String, String, Boolean>)
                (date1, date1Pattern, date2, date2Pattern) -> {

                    LocalDate localDate1 = LocalDate.parse(date1, DateTimeFormatter.ofPattern(date1Pattern));
                    LocalDate localDate2 = LocalDate.parse(date2, DateTimeFormatter.ofPattern(date2Pattern));
                    return localDate1.compareTo(localDate2) <= 0;
                };

        sparkSession.udf().register("date1LeqDate2", date1LessOrEqualThanDate2Udf, DataTypes.BooleanType);

        // return true if lowerDate (with pattern lowerDatePattern) <= date (with pattern datePattern) <= dateUpper (with pattern upperDatePattern)
        UDF6<String, String, String, String, String, String, Boolean> dateBetweenLowerDateAndUpperDate =
                (UDF6<String, String, String, String, String, String, Boolean>)
                        (stringDate, datePattern, stringLowerDate, lowerDatePattern, stringUpperDate, upperDatePattern)-> {

                            LocalDate localDate = LocalDate.parse(stringDate, DateTimeFormatter.ofPattern(datePattern));
                            LocalDate lowerDate = LocalDate.parse(stringLowerDate, DateTimeFormatter.ofPattern(lowerDatePattern));
                            LocalDate upperDate = LocalDate.parse(stringUpperDate, DateTimeFormatter.ofPattern(upperDatePattern));
                            return (localDate.compareTo(lowerDate) >= 0) & (localDate.compareTo(upperDate) <= 0);
                        };

        sparkSession.udf().register("dateBetween", dateBetweenLowerDateAndUpperDate, DataTypes.BooleanType);

    }

    private void registerUDFsForDateManipulation(){

        // UDFS THAT MANIPULATE DATES

        // AddDuration(ToDate((chararray)datainiziodef,'yyyyMMdd'),'$numero_mesi_1')
        UDF3<String, String, Integer, String> addDurationUdf = (UDF3<String, String, Integer, String>)
                (date, datePattern, months) -> {

                    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(datePattern);
                    LocalDate localDate = LocalDate.parse(date, dateTimeFormatter);
                    return localDate.plusMonths(months).format(dateTimeFormatter);

                };

        sparkSession.udf().register("addDuration", addDurationUdf, DataTypes.StringType);

        // SubtractDuration(ToDate((chararray)datainiziodef,'yyyyMMdd'),'$numero_mesi_1')
        UDF3<String, String, Integer, String> subtractDurationUdf = (UDF3<String, String, Integer, String>)
                (date, datePattern, months) -> {

                    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(datePattern);
                    LocalDate localDate = LocalDate.parse(date, dateTimeFormatter);
                    return localDate.minusMonths(months).format(dateTimeFormatter);
                };

        sparkSession.udf().register("substractDuration", subtractDurationUdf, DataTypes.StringType);

        // change date format
        UDF3<String, String, String, String> changeDateFormatUdf = (UDF3<String, String, String, String>)
                (inputDate, oldPattern, newPattern) -> {

                    if (inputDate != null){
                        LocalDate localDate = LocalDate.parse(inputDate, DateTimeFormatter.ofPattern(oldPattern));
                        return localDate.format(DateTimeFormatter.ofPattern(newPattern));
                    }
                    else {
                        return null;
                    }
                };

        sparkSession.udf().register("changeDateFormat", changeDateFormatUdf, DataTypes.StringType);

        // return the greatest date between two dates with same pattern
        UDF3<String, String, String, String> greatestDateUdf = (UDF3<String, String, String, String>)
                (stringFirstDate, stringSecondDate, commonPattern) -> {

                    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(commonPattern);
                    LocalDate firstDate = LocalDate.parse(stringFirstDate, dateTimeFormatter);
                    LocalDate secondDate = LocalDate.parse(stringSecondDate, dateTimeFormatter);
                    return firstDate.compareTo(secondDate) >= 0 ? firstDate.format(dateTimeFormatter) : secondDate.format(dateTimeFormatter);
                };

        sparkSession.udf().register("greatestDate", greatestDateUdf, DataTypes.StringType);

        // return the least date between two dates with same pattern
        UDF3<String, String, String, String> leastDateUdf = (UDF3<String, String, String, String>)
                (stringFirstDate, stringSecondDate, commonPattern) -> {

                    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(commonPattern);
                    LocalDate firstDate = LocalDate.parse(stringFirstDate, dateTimeFormatter);
                    LocalDate secondDate = LocalDate.parse(stringSecondDate, dateTimeFormatter);
                    return firstDate.compareTo(secondDate) <= 0 ? firstDate.format(dateTimeFormatter) : secondDate.format(dateTimeFormatter);
                };


        sparkSession.udf().register("leastDate", leastDateUdf, DataTypes.StringType);

        // return the number of days between two dates with same pattern, in absolute value
        // DaysBetween( ToDate((chararray)tlbcidef::datafinedef,'yyyyMMdd' ), ToDate((chararray)tlbpaspe_filter::datacont,'yyyyMMdd' ) ) as days_diff
        UDF3<String, String, String, Long> daysBetweenUdf = (UDF3<String, String, String, Long>)
                (stringFirstDate, stringSecondDate, commonPattern) -> {

                    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(commonPattern);
                    LocalDate firstDate = LocalDate.parse(stringFirstDate, dateTimeFormatter);
                    LocalDate secondDate = LocalDate.parse(stringSecondDate, dateTimeFormatter);
                    return Math.abs(firstDate.minusDays(secondDate.toEpochDay()).toEpochDay());

                };

        sparkSession.udf().register("daysBetween", daysBetweenUdf, DataTypes.LongType);

    }

    public void run(){}
}
