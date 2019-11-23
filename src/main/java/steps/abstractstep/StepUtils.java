package steps.abstractstep;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.expressions.WindowSpec;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.*;
import scala.collection.JavaConversions;
import scala.collection.Seq;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class StepUtils {

    protected Column castCol(Column column, DataType dataType){
        return column.cast(dataType);
    }

    // convert a string column representing a date from the given input format to the given output date format
    protected Column castStringColToDateCol(Column col, String inputF){

        return castCol(functions.from_unixtime(functions.unix_timestamp(col, inputF), "yyyy-MM-dd"), DataTypes.DateType);
    }

    // check if a date is within a given interval
    protected Column dateBetween(Column dateColumn, String dateColumnPattern, String lowerDate, String lowerDatePattern,
                                 String upperDate, String upperDatePattern){

        return functions.callUDF("dateBetween",
                dateColumn, functions.lit(dateColumnPattern),
                functions.lit(lowerDate), functions.lit(lowerDatePattern),
                functions.lit(upperDate), functions.lit(upperDatePattern));
    }

    // check if a date is <= other date
    protected Column dateLeqOtherDate(Column dateColumn, String dateColumnPattern, String otherDate, String otherDatePattern){

        return functions.callUDF("date1LeqDate2",
                dateColumn, functions.lit(dateColumnPattern),
                functions.lit(otherDate), functions.lit(otherDatePattern));

    }

    // change the format of string expressing a date
    protected Column dateFormat(Column dateColumn, String oldPattern, String newPattern){

        return functions.callUDF("changeDateFormat", dateColumn, functions.lit(oldPattern), functions.lit(newPattern));
    }

    protected Column getQuadJoinCondition(Dataset<Row> datasetLeft, Dataset<Row> datasetRight, List<String> joinColumnNames){

        Column joinCondition = datasetLeft.col(joinColumnNames.get(0))
                .equalTo(datasetRight.col(joinColumnNames.get(0).toUpperCase()));

        for (String joinColumnName: joinColumnNames.subList(1, joinColumnNames.toArray().length - 1)){

            joinCondition = joinCondition.and(datasetLeft.col(joinColumnName)
                    .equalTo(datasetRight.col(joinColumnName.toUpperCase())));
        }

        return joinCondition;
    }

    // create a schema with one String column for each name provided
    protected StructType getStringTypeSchema(List<String> columnNames){

        StructType schema = new StructType();
        for (String columnName: columnNames){

            schema = schema.add(new StructField(columnName, DataTypes.StringType, true, Metadata.empty()));
        }

        return schema;
    }

    // compute unixtimestamp for a generic column  using the given format
    protected Column getUnixTimeStampCol(Column column, String dateFormat){
        return functions.unix_timestamp(column, dateFormat);
    }

    // compute the least date between two date columns that have the same provided format
    protected Column leastDate(Column dateColumn1, Column dateColumn2, String commonDateFormat){

        Column column1Ts = getUnixTimeStampCol(dateColumn1, commonDateFormat);
        Column column2Ts = getUnixTimeStampCol(dateColumn2, commonDateFormat);
        return functions.least(column1Ts, column2Ts);
    }

    // compute the least date between two date columns that have different format
    protected Column leastDate(Column dateColumn1, Column dateColumn2, String date1Format, String date2Format){

        Column column1Ts = getUnixTimeStampCol(dateColumn1, date1Format);
        Column column2Ts = getUnixTimeStampCol(dateColumn2, date2Format);
        return functions.least(column1Ts, column2Ts);
    }

    protected LocalDate parseStringToLocalDate(String stringDate, String pattern){

        return LocalDate.parse(stringDate, DateTimeFormatter.ofPattern(pattern));
    }

    // replace oldString with newString and convert to Double column
    protected Column replaceAndConvertToDouble(Dataset<Row> df, String columnName, String oldString, String newString){

        return functions.regexp_replace(df.col(columnName), oldString, newString)
                .cast(DataTypes.DoubleType).as(columnName);
    }

    // create a list of columns to be selected from the given dataset
    protected List<Column> selectDfColumns(Dataset<Row> df, List<String> columnNames){

        List<Column> dfCols = new ArrayList<Column>();
        for (String columnName: columnNames){
            dfCols.add(df.col(columnName));
        }

        return dfCols;
    }

    // create a list of columns to be selected from the given dataset, giving an alias to each column
    protected List<Column> selectDfColumns(Dataset<Row> df, Map<String,String> columnMap){

        List<Column> dfCols = new ArrayList<Column>();
        Set<Map.Entry<String, String>> entryList = columnMap.entrySet();
        for (Map.Entry<String, String> entry: entryList){

            dfCols.add(df.col(entry.getKey()).alias(entry.getValue()));
        }

        return dfCols;
    }

    // convert a Java list into a scala.collection.Seq
    protected Seq<String> toScalaStringSeq(List<String> list){
        return JavaConversions.asScalaBuffer(list).toSeq();
    }

    protected Seq<Column> toScalaColSeq(List<Column> list) { return JavaConversions.asScalaBuffer(list).toSeq();}

    // creates a list of aggregate column expressions to be used over windowspec w on dataframe df
    protected List<Column> windowSum(Dataset<Row> df, Map<String, String> columnMap, WindowSpec w){

        List<Column> columnList = new ArrayList<>();
        Set<Map.Entry<String, String>> entryList = columnMap.entrySet();
        for (Map.Entry<String, String> entry: entryList){

            columnList.add(functions.sum(df.col(entry.getKey())).over(w).alias(entry.getValue()));
        }
        return columnList;
    }
}

