package it.carloni.luca.lgd.steps;

import it.carloni.luca.lgd.common.StepUtils;
import org.apache.log4j.Logger;
import org.apache.spark.sql.*;
import scala.collection.Seq;
import it.carloni.luca.lgd.common.AbstractStep;
import it.carloni.luca.lgd.schemas.RaccIncSchema;

import java.util.ArrayList;
import java.util.List;

import static it.carloni.luca.lgd.common.StepUtils.*;

public class RaccInc extends AbstractStep {

    private final Logger logger = Logger.getLogger(RaccInc.class);

    @Override
    public void run() {

        String tlbmignPathCsv = getValue("racc.inc.tlbmign.path.csv");
        String raccIncOutPath = getValue("racc.inc.racc.inc.out");

        logger.debug("tlbmignPathCsv: " + tlbmignPathCsv);
        logger.debug("raccIncOutPath: " + raccIncOutPath);

        Dataset<Row> tlbmign = readCsvAtPathUsingSchema(tlbmignPathCsv, fromPigSchemaToStructType(RaccIncSchema.getTlbmignPigSchema()));

        // AddDuration(ToDate(data_migraz,'yyyyMMdd'),'P1M') AS month_up
        Column monthUpCol = addDuration(tlbmign.col("data_migraz"), "yyyyMMdd", 1);

        List<Column> tlbmignSelectList = new ArrayList<>();
        tlbmignSelectList.add(tlbmign.col("cd_isti_ric").as("ist_ric_inc"));
        tlbmignSelectList.add(tlbmign.col("ndg_ric").as("ndg_ric_inc"));
        tlbmignSelectList.add(toStringCol(functions.lit(null)).as("num_ric_inc"));
        tlbmignSelectList.add(tlbmign.col("cd_isti_ced").as("ist_ced_inc"));
        tlbmignSelectList.add(tlbmign.col("ndg_ced").as("ndg_ced_inc"));
        tlbmignSelectList.add(toStringCol(functions.lit(null)).as("num_ced_inc"));
        tlbmignSelectList.add(tlbmign.col("data_migraz"));

        Seq<Column> tlbmignSelectSeq = StepUtils.toScalaSeq(tlbmignSelectList);
        Dataset<Row> raccIncOut = tlbmign.select(tlbmignSelectSeq)
                .withColumn("month_up", monthUpCol)
                .drop("data_migraz");

        writeDatasetAsCsvAtPath(raccIncOut, raccIncOutPath);
    }
}
