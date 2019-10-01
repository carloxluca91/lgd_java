package steps.lgdstep;

import org.apache.spark.sql.*;
import org.apache.spark.sql.types.StructType;
import steps.abstractstep.AbstractStep;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class QuadFposi extends AbstractStep {

    // required parameters
    private String ufficio;

    public QuadFposi(String ufficio){

        logger = Logger.getLogger(this.getClass().getName());

        this.ufficio = ufficio;

        stepInputDir = getProperty("quad.fposi.input.dir");
        stepOutputDir = getProperty("quad.fposi.output.dir");

        logger.info("stepInputDir: " + stepInputDir);
        logger.info("stepOutputDir: " + stepOutputDir);
        logger.info("$ufficio = " + this.ufficio);
    }

    @Override
    public void run() {

        String csvFormat = getProperty("csv.format");
        String hadoopFposiCsv = getProperty("hadoop.fposi.csv");

        logger.info("csvFormat: " + csvFormat);
        logger.info("hadoopFposiCsv: " + hadoopFposiCsv);

        // 17
        List<String> hadoopFposiColNames = Arrays.asList("codicebanca", "ndgprincipale", "datainiziodef", "datafinedef",
                "ndg_gruppo", "datainiziopd", "datainizioinc", "datainizioristrutt", "datainiziosoff", "totaccordatodatdef",
                "totutilizzdatdef", "naturagiuridica_segm", "intestazione", "codicefiscale_segm", "partitaiva_segm", "sae_segm",
                "rae_segm", "ciae_ndg", "provincia_segm", "ateco", "segmento", "databilseg", "strbilseg", "attivobilseg",
                "fatturbilseg");

        StructType hadoopFposiSchema = getDfSchema(hadoopFposiColNames);
        Dataset<Row> hadoopFposi = sparkSession.read().format(csvFormat).option("delimiter", ",").schema(hadoopFposiSchema)
                .csv(Paths.get(stepInputDir, hadoopFposiCsv).toString());

        // 45

        // 51
        String oldfposiLoadCsv = getProperty("old.fposi.load.csv");
        logger.info("oldfposiLoadCsv: " + oldfposiLoadCsv);

        List<String> oldfposiLoadColNames = Arrays.asList("datainizioDEF", "dataFINEDEF", "dataINIZIOPD", "datainizioinc",
                "dataSOFFERENZA", "codicebanca", "ndgprincipale", "flagincristrut", "cumulo");

        StructType oldfposiLoadSchema = getDfSchema(oldfposiLoadColNames);
        Dataset<Row> oldfposiLoad = sparkSession.read().format(csvFormat).option("delimiter", ",").schema(oldfposiLoadSchema)
                .csv(Paths.get(stepInputDir, oldfposiLoadCsv).toString());

        // 63

        // 70

        /*
        TODO: verifica date con solo due cifre per l'anno
        ToString(ToDate( datainizioDEF,'yy-MM-dd'),'yyyyMMdd')   as DATAINIZIODEF,
        ToString(ToDate( dataFINEDEF,'yy-MM-dd'),'yyyyMMdd')     as DATAFINEDEF,
        ToString(ToDate( dataINIZIOPD,'yy-MM-dd'),'yyyyMMdd')    as DATAINIZIOPD,
        ToString(ToDate( datainizioinc,'yy-MM-dd'),'yyyyMMdd')   as DATAINIZIOINC,
        ToString(ToDate( dataSOFFERENZA,'yy-MM-dd'),'yyyyMMdd')  as DATASOFFERENZA
         */

        Column DATAINIZIODEFCol = castStringColToDateCol(oldfposiLoad.col("datainizioDEF"), "yy-MM-dd").alias("DATAINIZIODEF");
        Column DATAFINEDEFCol = castStringColToDateCol(oldfposiLoad.col("dataFINEDEF"), "yy-MM-dd").alias("DATAFINEDEF");
        Column DATAINIZIOPDCol = castStringColToDateCol(oldfposiLoad.col("dataINIZIOPD"), "yy-MM-dd").alias("DATAINIZIOPD");
        Column DATAINIZIOINCCol = castStringColToDateCol(oldfposiLoad.col("datainizioinc"), "yy-MM-dd").alias("DATAINIZIOINC");
        Column DATASOFFERENZACol = castStringColToDateCol(oldfposiLoad.col("dataSOFFERENZA"), "yy-MM-dd").alias("DATASOFFERENZA");

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

        Column DATAINIZIODEFFilterCol = getUnixTimeStampCol(oldFposiGen.col("DATAINIZIODEF"), "yyyy-MM-dd").between(
                getUnixTimeStampCol(functions.lit("20070131"), "yyyyMMdd"),
                getUnixTimeStampCol(functions.lit("20071231"), "yyyyMMdd"));
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

        String hadoopFposiOutDir = getProperty("hadoop.fposi.out");
        String oldFposiOutDir = getProperty("old.fposi.out");
        String abbinatiOutDir = getProperty("abbinati.out");

        logger.info("hadoopFposiOutDir: " + hadoopFposiOutDir);
        logger.info("oldFposiOutDir: " + oldFposiOutDir);
        logger.info("abbinatiOutDir: " + abbinatiOutDir);

        hadoopFposiOut.write().format(csvFormat).option("delimiter", ",").mode(SaveMode.Overwrite).csv(
                Paths.get(stepOutputDir, hadoopFposiOutDir).toString());

        oldFposiOut.write().format(csvFormat).option("delimiter", ",").mode(SaveMode.Overwrite).csv(
                Paths.get(stepOutputDir, oldFposiOutDir).toString());

        abbinatiOut.write().format(csvFormat).option("delimiter", ",").mode(SaveMode.Overwrite).csv(
                Paths.get(stepOutputDir, abbinatiOutDir).toString());
    }
}
