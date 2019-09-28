package steps.lgdstep;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import steps.abstractstep.AbstractStep;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public class QuadFrapp extends AbstractStep {

    public QuadFrapp(){

        logger = Logger.getLogger(this.getClass().getName());

        stepInputDir = getProperty("quad.frapp.input.dir");
        stepOutputDir = getProperty("quad.frapp.output.dir");

        logger.info("stepInputDir: " + stepInputDir);
        logger.info("stepOutputDir: " + stepOutputDir);
    }

    @Override
    public void run() {

        String csvFormat = getProperty("csv.format");
        String hadoopFrappCsv = getProperty("hadoop.frapp.csv");
        String oldFrappLoadCsv = getProperty("old.frapp.load.csv");

        logger.info("csvFormat: " + csvFormat);
        logger.info("hadoopFrappCsv: " + hadoopFrappCsv);
        logger.info("oldFrappLoadCsv: " + oldFrappLoadCsv);

        List<String> hadoopFrappColumnNames = Arrays.asList("codicebanca", "ndg", "sportello", "conto", "datariferimento", "contoesteso",
                "formatecnica", "dataaccensione", "dataestinzione", "datascadenza", "r046tpammortam", "r071tprapporto", "r209periodliquid",
                "cdprodottorischio", "durataoriginaria", "r004divisa", "duarataresidua", "r239tpcontrrapp", "d2240marginefidorev",
                "d239marginefidoirrev", "d1788utilizzaccafirm", "d1780impaccordato", "r025flagristrutt", "d9322impaccorope",
                "d9323impaaccornonope", "d6464ggsconfino", "d0018fidonopesbfpi", "d0019fidopesbfpill", "d0623fidoopefirma",
                "d0009fidodpecassa", "d0008fidononopercas", "d0622fidononoperfir", "d0058fidonopesbfced", "d0059fidoopesbfced",
                "d0007autosconfcassa", "d0017autosconfsbf", "d0621autosconffirma", "d0967gamerci", "d0968garipoteca",
                "d0970garpersonale", "d0971garaltre", "d0985garobblpremiss", "d0986garobblbcentr", "d0987garobblaltre",
                "d0988gardepdenaro", "d0989garcdpremiss", "d0990garaltrivalori", "d0260valoredossier", "d0021fidoderiv", "d0061saldocont",
                "d0062aldocontdare", "d0063saldocontavere", "d0064partilliqsbf", "d0088indutilfidomed", "d0624impfidoaccfirma",
                "d0652imputilizzofirma", "d0982impgarperscred", "d1509impsallqmeddar", "d1785imputilizzocassa", "d0462impdervalintps",
                "d0475impdervalintng", "qcaprateascad", "qcaprateimpag", "qintrateimpag", "qcapratemora", "qintratemora", "accontiratescad",
                "imporigprestito", "d6998", "d6970", "d0075addebitiinsosp", "codicebanca_princ", "ndgprincipale", "datainiziodef");

        Dataset<Row> hadoopFrapp = sparkSession.read().format(csvFormat).option("sep", ",").schema(getDfSchema(hadoopFrappColumnNames))
                .csv(Paths.get(stepInputDir, hadoopFrappCsv).toString());

        List<String> oldFrappLoadColumnNames = Arrays.asList("CODICEBANCA", "NDG", "SPORTELLO", "CONTO", "PROGR_SEGMENTO", "DT_RIFERIMENTO",
                "CONTO_ESTESO", "FORMA_TECNICA", "DT_ACCENSIONE", "DT_ESTINZIONE", "DT_SCADENZA", "R046_TP_AMMORTAMENTO", "R071_TP_RAPPORTO",
                "R209_PERIOD_LIQUIDAZION", "CD_PRODOTTO_RISCHI", "DURATA_ORIGINARIA", "R004_DIVISA", "DURATA_RESIDUA", "R239_TP_CONTR_RAPP",
                "D2240_MARGINE_FIDO_REV", "D2239_MARGINE_FIDO_IRREV", "D1781_UTILIZZ_CASSA_FIRM", "D1780_IMP_ACCORDATO", "R025_FLAG_RISTRUTT",
                "D9322_IMP_ACCOR_OPE", "D9323_IMP_ACCOR_NON_OPE", "D6464_GG_SCONFINO", "D0018_FIDO_N_OPE_SBF_P_I", "D0019_FIDO_OPE_SBF_P_ILL",
                "D0623_FIDO_OPE_FIRMA", "D0009_FIDO_OPE_CASSA", "D0008_FIDO_NON_OPER_CAS", "D0622_FIDO_NON_OPER_FIR", "D0058_FIDO_N_OPE_SBF_CED",
                "D0059_FIDO_OPE_SBF_CED", "D0007_AUT_SCONF_CASSA", "D0017_AUT_SCONF_SBF", "D0621_AUT_SCONF_FIRMA", "D0967_GAR_MERCI",
                "D0968_GAR_IPOTECA", "D0970_GAR_PERSONALE", "D0971_GAR_ALTRE", "D0985_GAR_OBBL_PR_EMISS", "D0986_GAR_OBBL_B_CENTR",
                "D0987_GAR_OBBL_ALTRE", "D0988_GAR_DEP_DENARO", "D0989_GAR_CD_PR_EMISS", "D0990_GAR_ALTRI_VALORI", "D0260_VALORE_DOSSIER",
                "D0021_FIDO_DERIV", "D0061_SALDO_CONT", "D0062_SALDO_CONT_DARE", "D0063_SALDO_CONT_AVERE", "D0064_PART_ILLIQ_SBF",
                "D0088_IND_UTIL_FIDO_MED", "D0624_IMP_FIDO_ACC_FIRMA", "D0625_IMP_UTILIZZO_FIRMA", "D0982_IMP_GAR_PERS_CRED",
                "D1509_IMP_SAL_LQ_MED_DAR", "D1785_IMP_UTILIZZO_CASSA", "D0462_IMP_DER_VAL_INT_PS", "D0475_IMP_DER_VAL_INT_NG",
                "QCAPRATEASCAD", "QCAPRATEIMPAG", "QINTERRATEIMPAG", "QCAPRATEMORA", "QINTERRATEMORA", "ACCONTIRATESCAD", "IMPORIGPRESTITO",
                "CDFISC", "D6998_GAR_TITOLI", "D6970_GAR_PERS", "ADDEBITI_IN_SOSP");

        Dataset<Row> oldFrappLoad = sparkSession.read().format(csvFormat).option("sep", ",").schema(getDfSchema(oldFrappLoadColumnNames))
                .csv(Paths.get(stepInputDir, oldFrappLoadCsv).toString());

        List<String> fcollColumnNames = Arrays.asList();

    }
}