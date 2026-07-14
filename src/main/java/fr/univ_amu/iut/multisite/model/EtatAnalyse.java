package fr.univ_amu.iut.multisite.model;

import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.model.ReleveTraitement;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import java.util.Optional;

/// **Où en est l'analyse Tadarida d'une nuit, vue depuis le tableau multi-sites** (#1338).
///
/// L'observateur qui rentre de plusieurs semaines de terrain se pose une question **globale** :
/// *lesquelles de mes nuits sont prêtes à être importées ?* Cet état y répond en une colonne, là où il
/// fallait auparavant ouvrir chaque passage l'un après l'autre.
///
/// Il **croise deux questions distinctes**, que l'issue prend soin de ne pas confondre :
/// 1. *où en est le calcul côté serveur ?* — le relevé daté du cache (`participation_traitement`, #1262) ;
/// 2. *les observations sont-elles déjà en base ?* — l'existence de résultats d'identification (C12).
///
/// Une analyse `FINI` dont les résultats sont déjà importés n'est **pas** « à importer » : c'est tout
/// l'objet de [#IMPORTEE]. Sans ce croisement, la vue « résultats à importer » listerait indéfiniment des
/// nuits déjà traitées.
///
/// [#JAMAIS_RELEVE] n'est pas un état du serveur : c'est l'aveu que **nous** ne lui avons jamais posé la
/// question. Le cache est un relevé daté, pas une vérité (patron « État observé ») : ne pas savoir doit
/// se voir, plutôt que se déguiser en « jamais lancée ».
///
/// L'enum porte son propre badge (libellé, famille de couleur, infobulle), sur le patron de
/// `StatutPlateforme` : la vue applique la classe, elle ne choisit pas la couleur. Il vit dans `model`
/// (et non `viewmodel`) parce que [LignePassage], qui est une projection de `model`, le porte.
public enum EtatAnalyse {

    /// Nuit **non déposée** : le serveur n'a rien à analyser, la question ne se pose pas. Cellule vide
    /// plutôt qu'un libellé qui ferait du bruit sur la majorité des lignes.
    SANS_OBJET("", Familles.NEUTRE, "Cette nuit n'est pas déposée : il n'y a pas encore d'analyse à suivre."),

    /// Nuit déposée, mais **jamais interrogée** : on ne sait pas où en est le serveur, et on le dit.
    JAMAIS_RELEVE(
            "Jamais relevé",
            Familles.NEUTRE,
            "L'état de l'analyse n'a jamais été demandé à VigieChiro pour cette nuit."
                    + " Utilisez « Relever l'état des analyses » pour le savoir."),

    /// Le serveur répond, et la nuit **n'a jamais été calculée** : le dépôt ne lance pas l'analyse tout
    /// seul, il faut la demander.
    JAMAIS_LANCEE(
            "Jamais lancée",
            "badge-avertissement",
            "VigieChiro n'a jamais calculé cette nuit. Lancez l'analyse depuis l'écran de dépôt."),

    /// Le serveur travaille (planifiée, en cours, ou relancée après un échec rattrapé) : il n'y a rien à
    /// faire d'autre qu'attendre.
    EN_COURS("En cours", "badge-info", "VigieChiro analyse cette nuit. Comptez plusieurs dizaines de minutes."),

    /// Analyse **définitivement en échec** côté serveur.
    EN_ECHEC(
            "En échec",
            "badge-danger",
            "L'analyse a échoué sur VigieChiro. Ouvrez l'écran de dépôt pour en lire le motif et la relancer."),

    /// **Analyse terminée, résultats pas encore récupérés** : la seule ligne sur laquelle l'observateur a
    /// quelque chose à faire. C'est l'état que la vue mémorisée « Résultats à importer » isole.
    A_IMPORTER(
            "À importer",
            "badge-succes",
            "L'analyse est terminée et ses observations ne sont pas encore dans l'application :"
                    + " importez-les depuis l'écran du passage."),

    /// Analyse terminée **et** résultats déjà en base : rien à faire.
    IMPORTEE("Importée", Familles.NEUTRE, "Les observations de cette nuit sont déjà dans l'application.");

    /// Familles de couleur des badges, dans un porteur imbriqué : une constante d'enum ne peut pas
    /// référencer un champ statique de sa propre enum (référence en avant interdite par le langage).
    private static final class Familles {

        /// Les états qui **ne demandent rien** : hors sujet, information absente, ou travail déjà fait.
        private static final String NEUTRE = "badge-neutre";

        private Familles() {}
    }

    private final String libelle;
    private final String classeBadge;
    private final String infobulle;

    EtatAnalyse(String libelle, String classeBadge, String infobulle) {
        this.libelle = libelle;
        this.classeBadge = classeBadge;
        this.infobulle = infobulle;
    }

    /// Déduit l'état d'analyse d'une nuit en croisant son statut local, le **dernier relevé** du serveur
    /// (vide si on ne l'a jamais demandé) et la présence de résultats en base.
    ///
    /// L'ordre des tests porte le sens : on ne parle d'analyse que pour une nuit déposée ; on distingue
    /// « je ne sais pas » de « jamais lancée » ; et une analyse finie ne dit « à importer » que si les
    /// résultats manquent réellement.
    ///
    /// @param statut statut de workflow local du passage
    /// @param releve dernier relevé connu du traitement serveur, vide si jamais demandé
    /// @param resultatsImportes des résultats d'identification existent-ils déjà en base pour cette nuit ?
    public static EtatAnalyse deduire(
            StatutWorkflow statut, Optional<ReleveTraitement> releve, boolean resultatsImportes) {
        if (statut != StatutWorkflow.DEPOSE) {
            return SANS_OBJET;
        }
        if (releve.isEmpty()) {
            return JAMAIS_RELEVE;
        }
        Traitement traitement = releve.orElseThrow().traitement();
        if (traitement.estInconnu()) {
            return JAMAIS_LANCEE;
        }
        return switch (traitement.etat()) {
            case FINI -> resultatsImportes ? IMPORTEE : A_IMPORTER;
            case ERREUR -> EN_ECHEC;
            case PLANIFIE, EN_COURS, RETRY -> EN_COURS;
        };
    }

    /// Texte du badge (vide pour [#SANS_OBJET] : une nuit non déposée n'affiche rien).
    public String libelle() {
        return libelle;
    }

    /// Famille de couleur sémantique du badge : la vue applique la classe, elle ne choisit pas la couleur.
    public String classeBadge() {
        return classeBadge;
    }

    /// Infobulle du badge : ce que l'état **autorise ou demande**, pas seulement ce qu'il est (#801).
    public String infobulle() {
        return infobulle;
    }

    /// `true` si cette nuit a des observations **prêtes à être récupérées**. C'est le prédicat de la vue
    /// mémorisée « Résultats à importer ».
    public boolean aImporter() {
        return this == A_IMPORTER;
    }
}
