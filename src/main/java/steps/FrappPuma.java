package steps;

import org.apache.commons.cli.*;
import org.apache.spark.sql.*;
import org.apache.spark.sql.types.StructType;
import scala.collection.JavaConverters;
import scala.collection.Seq;

import java.nio.file.Paths;
import java.util.*;

public class FrappPuma extends AbstractStep{

    private String dataA;

    FrappPuma(String[] args){

        // define option for $data_a
        Option dataAOption = new Option("da", "dataA", true, "parametro $data_a");

        Options options = new Options();
        options.addOption(dataAOption);

        CommandLineParser commandLineParser = new BasicParser();

        // try to parse and retrieve command line arguments
        try {

            CommandLine commandLine = commandLineParser.parse(options, args);
            dataA = commandLine.getOptionValue("dataA");
        }
        catch (ParseException e) {
            e.printStackTrace();
        }

    }


    @Override
    public void run() {

        String csvFormat = getProperty("csv_format");
        String frappPumaInputDir = getProperty("FRAPP_PUMA_INPUT_DIR");
        String cicliNdgPath = getProperty("CICLI_NDG_PATH_CSV");
        String tlbgaranPath = getProperty("TLBGARAN_PATH");

        logger.info("csvFormat: " + csvFormat);
        logger.info("frappPumaInputDir: " + frappPumaInputDir);
        logger.info("cicliNdgPath: " + cicliNdgPath);
        logger.info("tlbgaranPath:" + tlbgaranPath);

        // 22
        List<String> tlbcidefColumns = Arrays.asList("codicebanca", "ndgprincipale", "datainiziodef", "datafinedef",
                "datainiziopd", "datainizioristrutt", "datainizioinc", "datainiziosoff", "c_key", "tipo_segmne", "sae_segm",
                "rae_segm", "segmento", "tp_ndg", "provincia_segm", "databilseg", "strbilseg", "attivobilseg", "fatturbilseg",
                "ndg_collegato", "codicebanca_collegato", "cd_collegamento", "cd_fiscale", "dt_rif_udct");
        StructType tlbcidefSchema = getDfSchema(tlbcidefColumns);
        Dataset<Row> tlbcidef = sparkSession.read().format(csvFormat).option("delimiter", ",").schema(tlbcidefSchema).csv(
                Paths.get(frappPumaInputDir, cicliNdgPath).toString());
        // 49

        // cicli_ndg_princ = FILTER tlbcidef BY cd_collegamento IS NULL;
        // cicli_ndg_coll = FILTER tlbcidef BY cd_collegamento IS NOT NULL;
        Dataset<Row> cicliNdgPrinc = tlbcidef.filter(tlbcidef.col("cd_collegamento").isNull());
        Dataset<Row> cicliNdgColl = tlbcidef.filter(tlbcidef.col("cd_collegamento").isNotNull());

        // 59
        List<String> tlbgaranColumns = Arrays.asList("cd_istituto", "ndg", "sportello", "dt_riferimento", "conto_esteso",
                "cd_puma2", "ide_garanzia", "importo", "fair_value");
        StructType tlbgaranSchema = getDfSchema(tlbgaranColumns);
        Dataset<Row> tlbgaran = sparkSession.read().format(csvFormat).option("delimiter", ",").schema(tlbgaranSchema).csv(
                Paths.get(frappPumaInputDir, tlbgaranPath).toString());

        // 71

        // JOIN  tlbgaran BY (cd_istituto, ndg), cicli_ndg_princ BY (codicebanca_collegato, ndg_collegato);
        Column joinCondition = tlbgaran.col("cd_istituto").equalTo(cicliNdgPrinc.col("codicebanca_collegato"))
                .and(tlbgaran.col("ndg").equalTo(cicliNdgPrinc.col("ndg_collegato")));

        // ToDate( (chararray)dt_riferimento,'yyyyMMdd') >= ToDate( (chararray)datainiziodef,'yyyyMMdd' )
        Column dtRiferimentoDataInizioDefFilterCol = getUnixTimeStampCol(tlbgaran.col("dt_riferimento"), "yyyyMMdd")
                .$greater$eq(getUnixTimeStampCol(tlbcidef.col("datainiziodef"), "yyyyMMdd"));

        /* and SUBSTRING( (chararray)dt_riferimento,0,6 ) <=
            SUBSTRING( (chararray)LeastDate( (int)ToString(SubtractDuration(ToDate((chararray)datafinedef,'yyyyMMdd' ),'P1M'),
                                                                                'yyyyMMdd') ,
                                             $data_a),
                        0,6 );
        */
        Column leastDateCol = functions.least(getUnixTimeStampCol(
                functions.add_months(convertStringColToDateCol(tlbcidef.col("datafinedef"),
                        "yyyyMMdd", "yyyy-MM-dd"), -1), "yyyy-MM-dd"),
                getUnixTimeStampCol(functions.lit(dataA), "yyyy-MM-dd"));

        Column subStringLeastDateCol = functions.substring(functions.from_unixtime(leastDateCol, "yyyyMMdd"), 0, 6);

        Column dtRiferimentoLeastDateFilterCol = getUnixTimeStampCol(
                functions.substring(tlbgaran.col("dt_riferimento"), 0, 6), "yyyyMM").$less$eq(
                        getUnixTimeStampCol(subStringLeastDateCol, "yyyyMM"));

        List<Column> selectColsList = new ArrayList<>(Collections.singletonList(tlbgaran.col("cd_istituto").alias("cd_isti")));
        List<String> tlbgaranSelectColNames = Arrays.asList("ndg", "sportello", "dt_riferimento", "conto_esteso",
                "cd_puma2", "ide_garanzia", "importo", "fair_value");
        List<String> cicliNdgSelectColNames = Arrays.asList("codicebanca", "sportello", "dt_riferimento");
        selectColsList.addAll(selectDfColumns(tlbgaran, tlbgaranSelectColNames));
        selectColsList.addAll(selectDfColumns(cicliNdgPrinc, cicliNdgSelectColNames));
        Seq<Column> selectColsSeq = JavaConverters.asScalaIteratorConverter(selectColsList.iterator()).asScala().toSeq();

        Dataset<Row> tlbcidefTlbgaranPrinc = tlbgaran.join(cicliNdgPrinc, joinCondition, "inner").filter(
                dtRiferimentoDataInizioDefFilterCol.and(dtRiferimentoLeastDateFilterCol)).select(selectColsSeq);

        // JOIN  tlbgaran BY (cd_istituto, ndg, dt_riferimento), cicli_ndg_coll BY (codicebanca_collegato, ndg_collegato, dt_rif_udct);
        joinCondition = tlbgaran.col("cd_istituto").equalTo(cicliNdgColl.col("codicebanca_collegato"))
                .and(tlbgaran.col("ndg").equalTo(cicliNdgColl.col("ndg_collegato")))
                .and(tlbgaran.col("dt_riferimento").equalTo(cicliNdgColl.col("dt_rif_udct")));

        selectColsList = new ArrayList<>(Collections.singletonList(tlbgaran.col("cd_istituto").alias("cd_isti")));
        selectColsList.addAll(selectDfColumns(tlbgaran, tlbgaranSelectColNames));
        selectColsList.addAll(selectDfColumns(cicliNdgColl, cicliNdgSelectColNames));
        selectColsSeq = JavaConverters.asScalaIteratorConverter(selectColsList.iterator()).asScala().toSeq();

        Dataset<Row> tlbcidefTlbgaranColl = tlbgaran.join(cicliNdgColl, joinCondition, "inner").select(selectColsSeq);

        Dataset<Row> frappPumaOut = tlbcidefTlbgaranPrinc.union(tlbcidefTlbgaranColl).distinct();

        String frappPumaOutputDir = getProperty("FRAPP_PUMA_OUTPUT_DIR");
        String frappPumaOutPath = getProperty("FRAPP_PUMA_OUT");

        logger.info("frappPumaOutputDir:" + frappPumaOutputDir);
        logger.info("frappPumaOutPath: " + frappPumaOutPath);

        frappPumaOut.write().format(csvFormat).option("delimiter", ",").mode(SaveMode.Overwrite).csv(
                Paths.get(frappPumaOutputDir, frappPumaOutPath).toString());
    }
}
