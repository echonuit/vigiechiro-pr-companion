package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.importation.model.Completude;
import fr.univ_amu.iut.importation.model.NuitDetectee;
import java.time.LocalDate;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;

/// Ligne de la **table des nuits** de M-Import : une [NuitDetectee] enrichie de l'état d'IHM que
/// l'utilisateur pilote (case **inclure**, n° de passage **proposé**) et d'un indicateur **« déjà
/// importée »** (#147) recalculé à l'inspection. Une carte laissée tourner plusieurs nuits produit une
/// ligne par nuit ; l'utilisateur décoche celles qu'il ne veut pas (ex. une nuit tronquée).
///
/// VM agnostique de l'IHM (règle ArchUnit `viewmodel_sans_javafx_ui`) : seul `javafx.beans` est
/// importé, jamais `javafx.scene`. Les champs purement descriptifs (date, nombre de fichiers,
/// complétude, motif) sont dérivés de la [NuitDetectee] immuable, sans `Property`.
public class NuitVM {

    private final NuitDetectee nuit;

    /// Inclure cette nuit dans l'import (une nuit incluse = un passage). **Vrai par défaut**, y compris
    /// pour une nuit tronquée (l'utilisateur peut la décocher au vu du badge « incomplète »).
    private final BooleanProperty inclure = new SimpleBooleanProperty(this, "inclure", true);

    /// N° de passage **proposé** pour cette nuit (auto-numérotation consécutive depuis le prochain n°
    /// libre du point). Recalculé par l'orchestrateur quand le rattachement ou les cases changent.
    private final IntegerProperty numeroPassagePropose = new SimpleIntegerProperty(this, "numeroPassagePropose", 0);

    /// Indicateur « nuit déjà importée » (#147) : non vide si un passage existe déjà en base pour cet
    /// enregistreur à cette date (doublon non bloquant, l'utilisateur reste libre d'importer).
    private final ReadOnlyStringWrapper statutDejaImportee = new ReadOnlyStringWrapper(this, "statutDejaImportee", "");

    public NuitVM(NuitDetectee nuit) {
        this.nuit = Objects.requireNonNull(nuit, "nuit");
    }

    /// La nuit détectée sous-jacente (pour assembler la demande d'import multi-nuits).
    public NuitDetectee nuit() {
        return nuit;
    }

    /// Date **du soir** de la nuit (date du futur passage, clé de tri).
    public LocalDate date() {
        return nuit.dateNuit();
    }

    /// Nombre d'enregistrements originaux (WAV) de la nuit.
    public int nombreFichiers() {
        return nuit.nombreFichiers();
    }

    /// `true` si le journal atteste une fin de nuit normale.
    ///
    /// **Faux ne veut pas dire tronquée** : il veut dire « pas attestée complète », ce qui couvre
    /// aussi la nuit dont le journal ne dit rien. Qui doit les distinguer lit [#badge()], qui les
    /// nomme.
    public boolean estComplete() {
        return nuit.completude() == Completude.COMPLETE;
    }

    /// Libellé court de l'état de complétude, à afficher en badge.
    ///
    /// « inconnue » n'est pas une nuance de « incomplète » : la nuit peut être entière, et rien ne
    /// le dit. Les confondre rendrait le badge menteur dans un sens comme dans l'autre (#4990).
    public String badge() {
        return switch (nuit.completude()) {
            case COMPLETE -> "complète";
            case TRONQUEE -> "incomplète";
            case INCONNUE -> "complétude inconnue";
        };
    }

    /// Classe CSS de la pastille de complétude, **dérivée** de l'état (jamais stockée). Le mapping reste
    /// côté feature : `commun` ne connaît que les énums de `commun.model`, sous peine de cycle
    /// d'architecture (même partage que `Fraicheur.classeBadge`).
    ///
    /// Une nuit tronquée est un **avertissement**, pas une erreur : elle s'importe et se dépose
    /// normalement, la troncature se constate. Une nuit inconnue est **neutre** : rien ne permet de
    /// l'inquiéter, rien ne permet de la rassurer, et lui donner la pastille verte reproduirait le
    /// défaut qu'on corrige (#4990).
    public String classeBadge() {
        return switch (nuit.completude()) {
            case COMPLETE -> "badge-succes";
            case TRONQUEE -> "badge-avertissement";
            case INCONNUE -> "badge-completude-inconnue";
        };
    }

    /// Ce que l'infobulle de la pastille dit, et **ce qu'il y a à faire** (#4990).
    ///
    /// Trois surfaces séparées portaient les trois pièces du raisonnement - le badge disait l'arrêt,
    /// la liste d'anomalies disait la batterie, la même liste disait les erreurs SD - et aucune ne
    /// les rapprochait. Un observateur les a rapprochées de tête et a conclu juste ; c'est ce
    /// rapprochement-là qui manquait.
    ///
    /// La phrase **relève**, elle ne diagnostique pas : « le journal s'arrête à 03:14, la dernière
    /// mesure de batterie était à 11 % » est honnête, « votre batterie était vide » ne l'est pas.
    /// Companion n'a que le journal du capteur.
    public String explication() {
        return switch (nuit.completude()) {
            case COMPLETE -> null;
            case TRONQUEE -> phraseTronquee();
            case INCONNUE ->
                "Le journal ne couvre pas cette nuit : ses entrées ont pu être effacées,"
                        + " une carte pleine effaçant les plus anciennes. La nuit est peut-être entière, et"
                        + " rien ici ne permet de l'affirmer. Ses enregistrements s'importent normalement.";
        };
    }

    /// Le motif de l'arrêt, puis ce que le journal montrait avant lui, puis la conduite à tenir.
    private String phraseTronquee() {
        StringBuilder phrase = new StringBuilder("La nuit ne s'est pas refermée normalement");
        if (nuit.motifIncompletude() != null) {
            phrase.append(" : ").append(nuit.motifIncompletude());
        }
        phrase.append(".");
        if (!nuit.indicesDuJournal().isEmpty()) {
            phrase.append("\n\nCe que le journal montre avant l'arrêt :");
            for (String indice : nuit.indicesDuJournal()) {
                phrase.append("\n  · ").append(indice);
            }
        }
        // La conduite à tenir, qui est ce qui manquait le plus : le badge énonçait un fait et
        // laissait sans réponse « est-ce exploitable » et « faut-il vérifier le matériel ».
        phrase.append("\n\nLes enregistrements de cette nuit restent exploitables jusqu'à l'arrêt, et")
                .append(" s'importent comme les autres. Vérifiez l'enregistreur avant la prochaine sortie.");
        return phrase.toString();
    }

    /// Motif de troncature (« carte SD pleine »…) quand la nuit est incomplète, sinon `null`.
    public String motifIncompletude() {
        return nuit.motifIncompletude();
    }

    /// Case « inclure cette nuit » (liée à la table de la vue).
    public BooleanProperty inclureProperty() {
        return inclure;
    }

    /// `true` si l'utilisateur a coché cette nuit (elle donnera un passage).
    public boolean estIncluse() {
        return inclure.get();
    }

    /// N° de passage proposé (liée à la colonne « Passage n° » de la vue).
    public IntegerProperty numeroPassageProposeProperty() {
        return numeroPassagePropose;
    }

    /// Valeur courante du n° de passage proposé (0 tant qu'aucun rattachement n'est choisi).
    public int numeroPassagePropose() {
        return numeroPassagePropose.get();
    }

    /// Fixe le n° de passage proposé (auto-numérotation par l'orchestrateur).
    public void definirNumeroPassagePropose(int numero) {
        numeroPassagePropose.set(numero);
    }

    /// Indicateur « déjà importée » (#147), vide si aucun doublon en base pour cette nuit.
    public ReadOnlyStringProperty statutDejaImporteeProperty() {
        return statutDejaImportee.getReadOnlyProperty();
    }

    /// Renseigne l'indicateur « déjà importée » (appelé par l'orchestrateur à l'inspection).
    void definirStatutDejaImportee(String statut) {
        statutDejaImportee.set(statut == null ? "" : statut);
    }
}
