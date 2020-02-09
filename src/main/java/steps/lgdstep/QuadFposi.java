package steps.lgdstep;

import org.apache.log4j.Logger;
import org.apache.spark.sql.*;
import steps.abstractstep.AbstractStep;
import steps.schemas.QuadFposiSchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static steps.abstractstep.StepUtils.*;

public class QuadFposi extends AbstractStep {

    // required parameters
    private String ufficio;

    public QuadFposi(String ufficio){

        logger = Logger.getLogger(QuadFposi.class);

        this.ufficio = ufficio;
        stepInputDir = getLGDPropertyValue("quad.fposi.input.dir");
        stepOutputDir = getLGDPropertyValue("quad.fposi.output.dir");

        logger.debug("ufficio: " + this.ufficio);
        logger.debug("stepInputDir: " + stepInputDir);
        logger.debug("stepOutputDir: " + stepOutputDir);

    }

    @Override
    public void run() {

        String hadoopFposiCsv = getLGDPropertyValue("quad.fposi.hadoop.fposi.csv");
        String oldfposiLoadCsv = getLGDPropertyValue("quad.fposi.old.fposi.load.csv");
        String hadoopFposiOutDir = getLGDPropertyValue("quad.fposi.hadoop.fposi.out");
        String oldFposiOutDir = getLGDPropertyValue("quad.fposi.old.fposi.out");
        String abbinatiOutDir = getLGDPropertyValue("quad.fposi.abbinati.out");

        logger.debug("hadoopFposiCsv: " + hadoopFposiCsv);
        logger.debug("oldfposiLoadCsv: " + oldfposiLoadCsv);
        logger.debug("hadoopFposiOutDir: " + hadoopFposiOutDir);
        logger.debug("oldFposiOutDir: " + oldFposiOutDir);
        logger.debug("abbinatiOutDir: " + abbinatiOutDir);

        // 17
        Dataset<Row> hadoopFposi = sparkSession.read().format(csvFormat).option("delimiter", ",")
                .schema(fromPigSchemaToStructType(QuadFposiSchema.getHadoopFposiPigSchema()))
                .csv(hadoopFposiCsv);

        // 51
        Dataset<Row> oldfposiLoad = sparkSession.read().format(csvFormat).option("delimiter", ",")
                .schema(fromPigSchemaToStructType(QuadFposiSchema.getOldFposiLoadPigSchema()))
                .csv(oldfposiLoadCsv);

        // 63

        // 70

        /*
        ToString(ToDate( datainizioDEF,'yy-MM-dd'),'yyyyMMdd')   as DATAINIZIODEF
        ,ToString(ToDate( dataFINEDEF,'yy-MM-dd'),'yyyyMMdd')     as DATAFINEDEF
        ,ToString(ToDate( dataINIZIOPD,'yy-MM-dd'),'yyyyMMdd')    as DATAINIZIOPD
        ,ToString(ToDate( datainizioinc,'yy-MM-dd'),'yyyyMMdd')   as DATAINIZIOINC
        ,ToString(ToDate( dataSOFFERENZA,'yy-MM-dd'),'yyyyMMdd')  as DATASOFFERENZA
         */

        String newDatePattern = "yyyyMMdd";
        Column DATAINIZIODEFCol = changeDateFormat(oldfposiLoad.col("datainizioDEF"), "yy-MM-dd", newDatePattern).alias("DATAINIZIODEF");
        Column DATAFINEDEFCol = changeDateFormat(oldfposiLoad.col("dataFINEDEF"), "yy-MM-dd", newDatePattern).alias("DATAFINEDEF");
        Column DATAINIZIOPDCol = changeDateFormat(oldfposiLoad.col("dataINIZIOPD"), "yy-MM-dd", newDatePattern).alias("DATAINIZIOPD");
        Column DATAINIZIOINCCol = changeDateFormat(oldfposiLoad.col("datainizioinc"), "yy-MM-dd", newDatePattern).alias("DATAINIZIOINC");
        Column DATASOFFERENZACol = changeDateFormat(oldfposiLoad.col("dataSOFFERENZA"), "yy-MM-dd", newDatePattern).alias("DATASOFFERENZA");

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

        Column DATAINIZIODEFFilterCol = isDateBetween(oldFposiGen.col("DATAINIZIODEF"), newDatePattern,
                "20070131", "yyyyMMdd", "20071231", "yyyyMMdd");
        Dataset<Row> oldFposi = oldFposiGen.filter(DATAINIZIODEFFilterCol);

        // 85

        // JOIN hadoop_fposi BY (codicebanca, ndgprincipale, datainiziodef) FULL OUTER, oldfposi BY (CODICEBANCA, NDGPRINCIPALE, DATAINIZIODEF);
        Column joinCondition = hadoopFposi.col("codicebanca").equalTo(oldFposi.col("CODICEBANCA"))
                .and(hadoopFposi.col("ndgprincipale").equalTo(oldFposi.col("NDGPRINCIPALE")))
                .and(hadoopFposi.col("datainiziodef").equalTo(oldFposi.col("DATAINIZIODEF")));

        Dataset<Row> hadoopFposiOldFposiJoin = hadoopFposi.join(oldFposi, joinCondition, "full_outer");

        List<Column> selectColList = new ArrayList<>(Collections.singletonList(functions.lit(ufficio).alias("ufficio")));
        List<Column> hadoopFposiSelectList = selectDfColumns(hadoopFposi, Arrays.asList(hadoopFposi.columns()));
        List<Column> oldFposiSelectList = selectDfColumns(oldFposi, Arrays.asList(oldFposi.columns()));

        selectColList.addAll(hadoopFposiSelectList);
        selectColList.addAll(oldFposiSelectList);

        Dataset<Row> hadoopFposiOut = hadoopFposiOldFposiJoin.filter(oldFposi.col("CODICEBANCA").isNull()).select(toScalaColSeq(selectColList));
        Dataset<Row> oldFposiOut = hadoopFposiOldFposiJoin.filter(hadoopFposi.col("codicebanca").isNull()).select(toScalaColSeq(selectColList));

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

        Dataset<Row> abbinatiOut = hadoopFposiOldFposiJoin.filter(abbinatiOutFilterCol).select(toScalaColSeq(selectColList));

        hadoopFposiOut.write().format(csvFormat).option("delimiter", ",").mode(SaveMode.Overwrite).csv(hadoopFposiOutDir);
        oldFposiOut.write().format(csvFormat).option("delimiter", ",").mode(SaveMode.Overwrite).csv(oldFposiOutDir);
        abbinatiOut.write().format(csvFormat).option("delimiter", ",").mode(SaveMode.Overwrite).csv(abbinatiOutDir);
    }
}
