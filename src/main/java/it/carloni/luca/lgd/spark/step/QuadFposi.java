package it.carloni.luca.lgd.spark.step;

import it.carloni.luca.lgd.parameter.step.UfficioValue;
import it.carloni.luca.lgd.schema.QuadFposiSchema;
import it.carloni.luca.lgd.spark.common.AbstractStep;
import org.apache.log4j.Logger;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;

import java.util.stream.Stream;

import static it.carloni.luca.lgd.spark.utils.StepUtils.changeDateFormatFromY2toY4UDF;

public class QuadFposi extends AbstractStep<UfficioValue> {

    private final Logger logger = Logger.getLogger(getClass());

    @Override
    public void run(UfficioValue ufficioValue) {

        String ufficio = ufficioValue.getUfficio();

        logger.info(ufficioValue);

        String hadoopFposiCsv = getValue("quad.fposi.hadoop.fposi.csv");
        String oldfposiLoadCsv = getValue("quad.fposi.old.fposi.load.csv");
        String hadoopFposiOutDir = getValue("quad.fposi.hadoop.fposi.out");
        String oldFposiOutDir = getValue("quad.fposi.old.fposi.out");
        String abbinatiOutDir = getValue("quad.fposi.abbinati.out");

        logger.info("quad.fposi.hadoop.fposi.csv: " + hadoopFposiCsv);
        logger.info("quad.fposi.old.fposi.load.csv: " + oldfposiLoadCsv);
        logger.info("quad.fposi.hadoop.fposi.out: " + hadoopFposiOutDir);
        logger.info("quad.fposi.old.fposi.out: " + oldFposiOutDir);
        logger.info("quad.fposi.abbinati.out: " + abbinatiOutDir);

        // 17
        Dataset<Row> hadoopFposi = readCsvAtPathUsingSchema(hadoopFposiCsv, QuadFposiSchema.getHadoopFposiPigSchema());

        // 51
        Dataset<Row> oldfposiLoad = readCsvAtPathUsingSchema(oldfposiLoadCsv, QuadFposiSchema.getOldFposiLoadPigSchema());

        // 70

        /*
        ToString(ToDate( datainizioDEF,'yy-MM-dd'),'yyyyMMdd')   as DATAINIZIODEF
        ,ToString(ToDate( dataFINEDEF,'yy-MM-dd'),'yyyyMMdd')     as DATAFINEDEF
        ,ToString(ToDate( dataINIZIOPD,'yy-MM-dd'),'yyyyMMdd')    as DATAINIZIOPD
        ,ToString(ToDate( datainizioinc,'yy-MM-dd'),'yyyyMMdd')   as DATAINIZIOINC
        ,ToString(ToDate( dataSOFFERENZA,'yy-MM-dd'),'yyyyMMdd')  as DATASOFFERENZA
         */

        String oldDatePattern = "yy-MM-dd";
        String newDatePattern = "yyyyMMdd";
        Column DATAINIZIODEFCol = changeDateFormatFromY2toY4UDF(oldfposiLoad.col("datainizioDEF"), oldDatePattern, newDatePattern).alias("DATAINIZIODEF");
        Column DATAFINEDEFCol = changeDateFormatFromY2toY4UDF(oldfposiLoad.col("dataFINEDEF"), oldDatePattern, newDatePattern).alias("DATAFINEDEF");
        Column DATAINIZIOPDCol = changeDateFormatFromY2toY4UDF(oldfposiLoad.col("dataINIZIOPD"), oldDatePattern, newDatePattern).alias("DATAINIZIOPD");
        Column DATAINIZIOINCCol = changeDateFormatFromY2toY4UDF(oldfposiLoad.col("datainizioinc"), oldDatePattern, newDatePattern).alias("DATAINIZIOINC");
        Column DATASOFFERENZACol = changeDateFormatFromY2toY4UDF(oldfposiLoad.col("dataSOFFERENZA"), oldDatePattern, newDatePattern).alias("DATASOFFERENZA");

        Dataset<Row> oldFposiGen = oldfposiLoad.select(DATAINIZIODEFCol, DATAFINEDEFCol, DATAINIZIOPDCol, DATAINIZIOINCCol, DATASOFFERENZACol,
                oldfposiLoad.col("codicebanca").alias("CODICEBANCA"), oldfposiLoad.col("ndgprincipale").alias("NDGPRINCIPALE"),
                oldfposiLoad.col("flagincristrut").alias("FLAGINCRISTRUT"), oldfposiLoad.col("cumulo").alias("CUMULO"));

        // 81

        // 83

        /*
        FILTER oldfposi_gen
        BY ToDate( DATAINIZIODEF,'yyyyMMdd') >= ToDate( '20070131','yyyyMMdd' )
        and ToDate( DATAINIZIODEF,'yyyyMMdd') <= ToDate( '20071231','yyyyMMdd' );
         */

        Column DATAINIZIODEFFilterCol = oldFposiGen.col("DATAINIZIODEF").between("20070131", "20071231");
        Dataset<Row> oldFposi = oldFposiGen.filter(DATAINIZIODEFFilterCol);

        // 85

        // JOIN hadoop_fposi BY (codicebanca, ndgprincipale, datainiziodef) FULL OUTER, oldfposi BY (CODICEBANCA, NDGPRINCIPALE, DATAINIZIODEF);
        Column joinCondition = Stream.of("codicebanca", "ndgprincipale", "datainiziodef")
                .map(columnName -> hadoopFposi.col(columnName).equalTo(oldFposi.col(columnName.toUpperCase())))
                .reduce(Column::and)
                .get();

        Dataset<Row> hadoopFposiOldFposiJoin = hadoopFposi.join(oldFposi, joinCondition, "full_outer");

        Column ufficioCol = functions.lit(ufficio).alias("ufficio");

        Dataset<Row> hadoopFposiOut = hadoopFposiOldFposiJoin
                .filter(oldFposi.col("CODICEBANCA").isNull())
                .select(ufficioCol, hadoopFposi.col("*"), oldFposi.col("*"));

        hadoopFposiOut.show();

        Dataset<Row> oldFposiOut = hadoopFposiOldFposiJoin
                .filter(hadoopFposi.col("codicebanca").isNull())
                .select(ufficioCol, hadoopFposi.col("*"), oldFposi.col("*"));

        oldFposiOut.show();

        /*
        FILTER hadoop_fposi_oldfposi_join
        BY hadoop_fposi::codicebanca IS NOT NULL
        AND oldfposi::CODICEBANCA IS NOT NULL
        AND hadoop_fposi::datafinedef == '99991231'
        AND hadoop_fposi::datainiziopd       != oldfposi::DATAINIZIOPD
        AND hadoop_fposi::datainizioinc      != oldfposi::DATAINIZIOINC
        AND hadoop_fposi::datainiziosoff     != oldfposi::DATASOFFERENZA
         */

        Column abbinatiOutFilterCol = hadoopFposi.col("codicebanca").isNotNull()
                .and(oldFposi.col("CODICEBANCA").isNotNull())
                .and(hadoopFposi.col("datafinedef").equalTo("99991231"))
                .and(hadoopFposi.col("datainiziopd").notEqual(oldFposi.col("DATAINIZIOPD")))
                .and(hadoopFposi.col("datainizioinc").notEqual(oldFposi.col("DATAINIZIOINC")))
                .and(hadoopFposi.col("datainiziosoff").notEqual(oldFposi.col("DATASOFFERENZA")));

        Dataset<Row> abbinatiOut = hadoopFposiOldFposiJoin
                .filter(abbinatiOutFilterCol)
                .select(ufficioCol, hadoopFposi.col("*"), oldFposi.col("*"));

        writeDatasetAsCsvAtPath(hadoopFposiOut, hadoopFposiOutDir);
        writeDatasetAsCsvAtPath(oldFposiOut, oldFposiOutDir);
        writeDatasetAsCsvAtPath(abbinatiOut, abbinatiOutDir);
    }
}
