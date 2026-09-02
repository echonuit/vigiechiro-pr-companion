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
    /// @param appuiSurTouche si vrai, `Wakeup by PINPUSH... Cpt 2` s'intercale au milieu de la nuit :
    ///     l'observateur est venu regarder l'écran. Ce réveil est **voulu** et n'ouvre pas une nuit de
    ///     plus, ce que le code ignorait (#4981)
    /// @param nuitInterrompue si vrai, le journal s'arrête après le réveil, sans mise en veille : le
    ///     cycle reste ouvert et la nuit est TRONQUÉE. Carte pleine, batterie vide, arrêt subi (#5093)
    /// @param sessions redémarrages **supplémentaires** du capteur, chacun reposant ses paramètres après
    ///     `nuit` (#3898). Vide dans le cas courant, et le journal produit est alors **identique** à
    ///     l'octet près à ce qu'il était avant : les huit specs existantes ne bougent pas
    record Journal(
            boolean present,
            String serie,
            LocalDate nuit,
            boolean sondePresente,
            boolean corrompu,
            boolean appuiSurTouche,
            boolean nuitInterrompue,
            List<Session> sessions) {

        Journal {
            sessions = List.copyOf(sessions);
        }
    }

    /// Un **redémarrage du capteur**, qui repose ses paramètres d'acquisition (#3898).
    ///
    /// ## Pourquoi une carte en porte plusieurs
    ///
    /// Laisser l'enregistreur plusieurs nuits au même point est le cas **courant** d'un protocole Point
    /// Fixe. Entre deux séries, on le reprend, on le reconfigure, on le repose : le journal accumule
    /// alors une ligne « Paramètres » par session, et #3460 a corrigé le fait qu'une nuit repartait
    /// avec les réglages d'une **autre**.
    ///
    /// Aucune fixture ne portait ce cas, si bien que la correction n'était vérifiable que par la CI.
    ///
    /// @param nuit la nuit que cette session ouvre
    /// @param frequenceKhz la fréquence d'acquisition qu'elle annonce, en kHz
    record Session(LocalDate nuit, int frequenceKhz) {}

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
            int rejets,
            List<String> completudes) {

        Attendu {
            completudes = List.copyOf(completudes);
        }
    }
}
