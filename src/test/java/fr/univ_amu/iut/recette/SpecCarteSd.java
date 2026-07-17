package fr.univ_amu.iut.recette;

import java.time.LocalDate;
import java.util.List;

/// Spec déclarative d'une carte SD de recette, lue depuis `recette/fixtures/spec/<fixture>.yaml`
/// par [LecteurSpec] et matérialisée sur disque par [GenerateurCartesSD].
///
/// Le but est de décrire chaque carte de recette (nominale, mélange, incohérente...) en quelques
/// kilo-octets versionnables plutôt qu'en centaines de méga-octets binaires : la spec est la source
/// de vérité, l'arbre SD n'en est qu'un artefact reconstructible **à l'identique** (aucune date tirée
/// de l'horloge, aucun octet aléatoire).
///
/// @param fixture nom court de la carte (sert de nom de sous-dossier généré), ex. `sd-nominale`
/// @param but phrase décrivant la pathologie exercée (documentation, non utilisée par le générateur)
/// @param journal journal du capteur `LogPR<serie>.txt`
/// @param thlog relevé climatique `PaRecPR<serie>_THLog.csv`
/// @param wav paramètres communs des enregistrements WAV générés
/// @param enregistreurs enregistreurs présents dans `bruts/` (un ou plusieurs pour le cas « mélange »)
/// @param prefixe préfixe de session à appliquer aux noms de WAV (`Car...`), ou `null` s'ils restent bruts
/// @param zip si vrai, une archive `<fixture>.zip` de l'arbre est aussi produite (chemin décompression)
/// @param attendu contrat de recette : ce que l'inspection réelle du code d'import doit constater
record SpecCarteSd(
        String fixture,
        String but,
        Journal journal,
        Thlog thlog,
        Wav wav,
        List<Enregistreur> enregistreurs,
        Prefixe prefixe,
        boolean zip,
        Attendu attendu) {

    SpecCarteSd {
        enregistreurs = List.copyOf(enregistreurs);
    }

    /// Journal du capteur. Quand `present` est faux, aucun `LogPR` n'est écrit (cas mode dégradé).
    ///
    /// @param present présence du fichier journal
    /// @param serie n° de série d'enregistreur déclaré par le journal (sert aussi de nom de fichier)
    /// @param nuit date de la première ligne du journal (fixe `dateDebut` pour l'analyseur)
    /// @param sondePresente ajoute (ou non) la ligne « Sonde température/hygrométrie présente »
    /// @param corrompu si vrai, le journal est écrit illisible (aucune série extractible : l'inspection échoue)
    record Journal(boolean present, String serie, LocalDate nuit, boolean sondePresente, boolean corrompu) {}

    /// Relevé climatique. Quand `present` est faux, aucun `THLog.csv` n'est écrit (R20).
    ///
    /// @param present présence du fichier de relevé
    /// @param mesures nombre de lignes de mesures déterministes à générer
    record Thlog(boolean present, int mesures) {}

    /// Paramètres communs des WAV générés (en-tête RIFF/WAVE mono 16 bits valide).
    ///
    /// @param frequenceHz fréquence d'échantillonnage inscrite dans l'en-tête (R10 : divisible par 10)
    /// @param dureeSecondes durée de chaque enregistrement (courte, pour des fixtures légères)
    record Wav(int frequenceHz, double dureeSecondes) {}

    /// Un enregistreur et les horodatages de ses fichiers. Le nom produit pour chaque horodatage est
    /// `PaRecPR<serie>_<horodatage>.wav` (motif attendu par l'import), avec `horodatage` au format
    /// `yyyyMMdd_HHmmss` (préfixé `Car...` si la spec porte un [Prefixe]).
    ///
    /// @param serie n° de série de cet enregistreur
    /// @param horodatages horodatages `yyyyMMdd_HHmmss` des enregistrements **valides**
    /// @param fauxWav horodatages écrits en octets non-WAV (faux fichiers : rejetés à l'import)
    record Enregistreur(String serie, List<String> horodatages, List<String> fauxWav) {
        Enregistreur {
            horodatages = List.copyOf(horodatages);
            fauxWav = List.copyOf(fauxWav);
        }
    }

    /// Préfixe de session R6 appliqué aux noms de WAV : `Car<carre>-<annee>-Pass<passage>-<point>-`.
    ///
    /// @param carre identifiant du carré (ex. `130711`)
    /// @param annee année du passage
    /// @param passage numéro de passage
    /// @param point code du point d'écoute (ex. `Z1`)
    record Prefixe(String carre, int annee, int passage, String point) {}

    /// Contrat de recette : signaux que l'inspection (et l'import, pour les rejets) réels doivent
    /// produire. Le garde-fou génère la carte puis confronte ces valeurs au résultat du code, pas à une
    /// liste tenue à la main.
    ///
    /// @param aJournal un journal a été localisé et parsé
    /// @param aReleve un relevé climatique accompagne la nuit
    /// @param journalLisible l'inspection aboutit (faux : le journal illisible fait échouer l'inspection)
    /// @param plusieursEnregistreurs les WAV portent plus d'un n° de série (bandeau « mélange »)
    /// @param incoherent l'identité déclarée contredit les enregistrements (bandeau « incohérence »)
    /// @param nuits nombre de nuits détectées par la partition (1 = import classique)
    /// @param etatNommage état de nommage attendu : `BRUT`, `PREFIXE` ou `VIDE`
    /// @param rejets nombre de fichiers rejetés à l'import réel (> 0 déclenche la vérification d'import)
    record Attendu(
            boolean aJournal,
            boolean aReleve,
            boolean journalLisible,
            boolean plusieursEnregistreurs,
            boolean incoherent,
            int nuits,
            String etatNommage,
            int rejets) {}
}
