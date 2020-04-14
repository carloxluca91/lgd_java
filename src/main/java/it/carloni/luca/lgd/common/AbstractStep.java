package it.carloni.luca.lgd.common;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.log4j.Logger;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import it.carloni.luca.lgd.common.udfs.UDFsFactory;
import it.carloni.luca.lgd.common.udfs.UDFsNames;

public abstract class AbstractStep {

    private final SparkSession sparkSession = getSparkSessionWithUDFs();
    private final PropertiesConfiguration properties = new PropertiesConfiguration();

    private String csvDelimiter;
    protected String dataDaPattern;
    protected String dataAPattern;
    protected String csvFormat;

    protected AbstractStep(){

        Logger logger = Logger.getLogger(AbstractStep.class);

        try {

            properties.load(AbstractStep.class.getClassLoader().getResourceAsStream("lgd.properties"));

            csvFormat = getValue("csv.format");
            csvDelimiter = getValue("csv.delimiter");
            dataDaPattern = getValue("params.datada.pattern");
            dataAPattern = getValue("params.dataa.pattern");

            logger.debug("csvFormat: " + csvFormat);
            logger.debug("csvDelimiter: " + csvDelimiter);
            logger.debug("dataDaPattern: " + dataDaPattern);
            logger.debug("dataAPattern: " + dataAPattern);
        }
        catch (ConfigurationException e){

            logger.error("ConfigurationException occurred");
            logger.error(e);
        }
    }

    public String getValue(String key) {
        return properties.getString(key);
    }

    private SparkSession getSparkSessionWithUDFs(){

        SparkSession sparkSession = SparkSession.builder().getOrCreate();
        return registerUDFs(sparkSession);
    }

    private SparkSession registerUDFs(SparkSession sparkSession){

        sparkSession.udf().register(UDFsNames.ADD_DURATION_UDF_NAME, UDFsFactory.addDurationUDF(), DataTypes.StringType);
        sparkSession.udf().register(UDFsNames.SUBTRACT_DURATION_UDF_NAME, UDFsFactory.substractDurationUDF(), DataTypes.StringType);
        sparkSession.udf().register(UDFsNames.CHANGE_DATE_FORMAT_UDF_NAME, UDFsFactory.changeDateFormatUDF(), DataTypes.StringType);
        sparkSession.udf().register(UDFsNames.DAYS_BETWEEN_UDF_NAME, UDFsFactory.daysBetweenUDF(), DataTypes.LongType);
        sparkSession.udf().register(UDFsNames.GREATEST_DATE_UDF_NAME, UDFsFactory.greatestDateUDF(), DataTypes.StringType);
        sparkSession.udf().register(UDFsNames.IS_DATE_BETWEEN_UDF_NAME, UDFsFactory.isDateBetweenLowerDateAndUpperDateUDF(), DataTypes.BooleanType);
        sparkSession.udf().register(UDFsNames.LEAST_DATE_UDF_NAME, UDFsFactory.leastDateUDF(), DataTypes.StringType);

        return sparkSession;
    }

    protected void registerStepUDF(UDF1<String, String> udf, String udfName, DataType udfReturnType) {

        sparkSession.udf().register(udfName, udf, udfReturnType);
    }


    protected Dataset<Row> readCsvAtPathUsingSchema(String csvFilePath, StructType csvSchema){

        return sparkSession.read().format(csvFormat).option("sep", csvDelimiter).schema(csvSchema).csv(csvFilePath);
    }

    protected void writeDatasetAsCsvAtPath(Dataset<Row> dataset, String path){

        dataset.coalesce(1).write().format(csvFormat).option("sep", csvDelimiter).mode(SaveMode.Overwrite).csv(path);
    }

    abstract public void run();
}
