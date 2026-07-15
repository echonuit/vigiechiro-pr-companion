package fr.univ_amu.iut.qualification.model;

import fr.univ_amu.iut.commun.model.Alerte;
import fr.univ_amu.iut.commun.model.ResultatVerification;
import java.util.Objects;

/// Pré-check synthétique d'une nuit d'enregistrement (P3, étape 1) : produit **trois feux**
/// (🟢 vert / 🟠 orange / 🔴 rouge) résumant l'état de la nuit *sans écoute*.
///
/// - **Couverture horaire** : la plage observée couvre-t-elle la fenêtre théorique de la
///   nuit (R3) ? 🟠 si l'écart dépasse {@value #TOLERANCE_COUVERTURE_MINUTES} min d'un côté,
///   🔴 si une moitié de nuit complète manque.
/// - **Nombre de fichiers** : 🔴 si aucun fichier, 🟠 si la nuit est anormalement creuse
///   (< {@value #SEUIL_FICHIERS_CREUX}), 🟢 sinon.
/// - **Cohérence du renommage** : 🔴 dès qu'un fichier diverge du préfixe attendu (R6), 🟢
///   sinon.
///
/// **Moteur pur.** [#evaluer(Mesures)] décide les feux à partir de mesures déjà calculées
/// ([Mesures]) : aucune base, aucune IHM, aucun parsing ici (le comptage des fichiers, le
/// calcul de l'écart de couverture et la vérification du préfixe relèvent de
/// `ServiceQualification`, qui dispose des DAO). On peut donc tester tous les feux en JUnit
/// nu.
///
/// **Consultatif (R13).** Le pré-check ne bloque jamais le workflow : l'utilisateur reste
/// responsable, aucun seuil n'est imposé. La précision des trois couleurs est portée par
/// [Diagnostic] ; sa conversion [Diagnostic#versResultatVerification()] n'émet donc que des
/// alertes **soft** (jamais bloquantes), conformément au patron « règle soft →
/// [ResultatVerification] ». La seule règle réellement bloquante de cette chaîne est R14
/// (verdict « À jeter » ⇒ exclu d'un lot), traitée en aval par la feature `lot`.
public class PreCheckNuit {

    /// En-deçà de ce nombre d'enregistrements, la nuit est jugée anormalement creuse (🟠).
    public static final int SEUIL_FICHIERS_CREUX = 50;

    /// Tolérance (minutes) sur la couverture horaire avant de passer le feu à l'orange.
    public static final long TOLERANCE_COUVERTURE_MINUTES = 30;

    /// Couleur d'un indicateur du pré-check.
    public enum Feu {
        VERT,
        ORANGE,
        ROUGE
    }

    /// Mesures brutes d'une nuit, calculées en amont (par `ServiceQualification`) et fournies
    /// au moteur. Toutes les grandeurs numériques sont ≥ 0.
    ///
    /// @param nombreFichiers nombre d'enregistrements originaux de la session
    /// @param fichiersMalNommes nombre d'originaux dont le nom diverge du préfixe attendu (R6)
    /// @param ecartCouvertureMinutes plus grand déficit de couverture observé d'un côté (minutes)
    /// @param moitieNuitManquante `true` si une moitié de nuit complète manque
    /// @param plageObservee plage horaire réellement enregistrée, déjà formatée (ex. `20:25 à 07:47`),
    ///     ou `null` si non mesurable (aucun horodatage exploitable)
    /// @param plageAttendue fenêtre théorique de la nuit, déjà formatée (ex. `20:57 à 07:12`), ou
    ///     `null` si non calculable
    public record Mesures(
            int nombreFichiers,
            int fichiersMalNommes,
            long ecartCouvertureMinutes,
            boolean moitieNuitManquante,
            String plageObservee,
            String plageAttendue) {

        public Mesures {
            if (nombreFichiers < 0 || fichiersMalNommes < 0 || ecartCouvertureMinutes < 0) {
                throw new IllegalArgumentException("Les mesures de la nuit doivent être positives.");
            }
        }

        /// Constructeur de compatibilité (sans les plages horaires formatées) : préserve les appels
        /// antérieurs à #1506, où le pré-check ne rapportait que les couleurs. Les explications de
        /// couverture retombent alors sur une formulation générique.
        public Mesures(
                int nombreFichiers, int fichiersMalNommes, long ecartCouvertureMinutes, boolean moitieNuitManquante) {
            this(nombreFichiers, fichiersMalNommes, ecartCouvertureMinutes, moitieNuitManquante, null, null);
        }
    }

    /// Les trois feux du pré-check, **chacun accompagné de son explication** (#1506) : la mesure
    /// et l'écart au protocole en clair (ex. « Nombre de fichiers : 6 originaux, moins que les 50
    /// attendus… »), pour que l'utilisateur sache ce qui a été mesuré et quoi en faire, plutôt
    /// qu'un « à surveiller » opaque.
    ///
    /// @param couvertureHoraire couverture de la fenêtre théorique de la nuit (R3)
    /// @param nombreFichiers volume d'enregistrements
    /// @param coherenceRenommage conformité des noms de fichiers au préfixe attendu (R6)
    /// @param detailCouverture explication en clair du feu de couverture horaire
    /// @param detailNombre explication en clair du feu de nombre de fichiers
    /// @param detailRenommage explication en clair du feu de cohérence du renommage
    public record Diagnostic(
            Feu couvertureHoraire,
            Feu nombreFichiers,
            Feu coherenceRenommage,
            String detailCouverture,
            String detailNombre,
            String detailRenommage) {

        public Diagnostic {
            Objects.requireNonNull(couvertureHoraire, "couvertureHoraire");
            Objects.requireNonNull(nombreFichiers, "nombreFichiers");
            Objects.requireNonNull(coherenceRenommage, "coherenceRenommage");
        }

        /// Constructeur de compatibilité (couleurs seules) : préserve les appels antérieurs à #1506
        /// (mocks de test, restitutions simplifiées). Les explications retombent sur une
        /// formulation générique dérivée de la couleur.
        public Diagnostic(Feu couvertureHoraire, Feu nombreFichiers, Feu coherenceRenommage) {
            this(
                    couvertureHoraire,
                    nombreFichiers,
                    coherenceRenommage,
                    detailGenerique("Couverture horaire", couvertureHoraire),
                    detailGenerique("Nombre de fichiers", nombreFichiers),
                    detailGenerique("Cohérence du renommage", coherenceRenommage));
        }

        /// `true` si les trois feux sont au vert (rien à signaler).
        public boolean toutAuVert() {
            return couvertureHoraire == Feu.VERT && nombreFichiers == Feu.VERT && coherenceRenommage == Feu.VERT;
        }

        /// `true` si au moins un feu est au rouge (anomalie à examiner).
        public boolean presenteUneAnomalie() {
            return couvertureHoraire == Feu.ROUGE || nombreFichiers == Feu.ROUGE || coherenceRenommage == Feu.ROUGE;
        }

        /// Résumé pour la barre de statut : **nomme les feux en cause** (au rouge) plutôt qu'un
        /// « anomalie signalée » anonyme (#1506). Chaîne vide si aucun feu n'est au rouge.
        public String resumeAnomalie() {
            StringBuilder enCause = new StringBuilder();
            ajouterSiRouge(enCause, couvertureHoraire, "couverture horaire");
            ajouterSiRouge(enCause, nombreFichiers, "nombre de fichiers");
            ajouterSiRouge(enCause, coherenceRenommage, "cohérence du renommage");
            if (enCause.isEmpty()) {
                return "";
            }
            return "Anomalie au pré-check : " + enCause + " (consultatif, non bloquant).";
        }

        private static void ajouterSiRouge(StringBuilder cumul, Feu feu, String nom) {
            if (feu == Feu.ROUGE) {
                if (!cumul.isEmpty()) {
                    cumul.append(", ");
                }
                cumul.append(nom);
            }
        }

        private static String detailGenerique(String libelle, Feu feu) {
            return switch (feu) {
                case VERT -> libelle + " : conforme.";
                case ORANGE -> libelle + " : à surveiller (léger écart au protocole).";
                case ROUGE -> libelle + " : anomalie détectée.";
            };
        }

        /// Restitue le diagnostic « sous forme de [ResultatVerification] » pour l'IHM : un feu
        /// vert n'ajoute rien, un feu orange ou rouge ajoute une alerte **soft** (le pré-check
        /// est consultatif, R13). `estConforme()` équivaut donc à [#toutAuVert()], et
        /// `estBloquant()` est toujours `false`.
        public ResultatVerification versResultatVerification() {
            ResultatVerification resultat = ResultatVerification.ok();
            resultat = ajouter(resultat, couvertureHoraire, "Couverture horaire");
            resultat = ajouter(resultat, nombreFichiers, "Nombre de fichiers");
            resultat = ajouter(resultat, coherenceRenommage, "Cohérence du renommage");
            return resultat;
        }

        private static ResultatVerification ajouter(ResultatVerification resultat, Feu feu, String libelle) {
            return switch (feu) {
                case VERT -> resultat;
                case ORANGE -> resultat.avec(Alerte.soft(libelle + " : à surveiller (feu orange)."));
                case ROUGE -> resultat.avec(Alerte.soft(libelle + " : anomalie détectée (feu rouge)."));
            };
        }
    }

    /// Décide les trois feux à partir des mesures de la nuit, **et rédige l'explication** de
    /// chacun (mesure + écart au protocole, #1506).
    public Diagnostic evaluer(Mesures mesures) {
        Objects.requireNonNull(mesures, "mesures");
        Feu couverture = evaluerCouverture(mesures);
        Feu nombre = evaluerNombre(mesures);
        Feu renommage = evaluerRenommage(mesures);
        return new Diagnostic(
                couverture,
                nombre,
                renommage,
                detailCouverture(couverture, mesures),
                detailNombre(nombre, mesures),
                detailRenommage(renommage, mesures));
    }

    /// Explique le feu de **couverture horaire** : plage enregistrée vs fenêtre théorique de la
    /// nuit, et nature de l'écart. Retombe sur une formulation courte si les plages ne sont pas
    /// mesurables (cas neutre, feu vert).
    private static String detailCouverture(Feu feu, Mesures mesures) {
        if (mesures.plageObservee() == null || mesures.plageAttendue() == null) {
            return "Couverture horaire : conforme (couverture non mesurable en détail).";
        }
        String base = "Couverture horaire : enregistrements de " + mesures.plageObservee() + ", nuit de "
                + mesures.plageAttendue();
        return switch (feu) {
            case VERT -> base + " : plage bien couverte.";
            case ORANGE ->
                base + " : écart de " + mesures.ecartCouvertureMinutes() + " min (tolérance "
                        + TOLERANCE_COUVERTURE_MINUTES + " min).";
            case ROUGE -> base + " : au moins une moitié de nuit hors fenêtre (une partie est diurne).";
        };
    }

    /// Explique le feu de **nombre de fichiers** : volume observé vs volume attendu.
    private static String detailNombre(Feu feu, Mesures mesures) {
        return switch (feu) {
            case VERT -> "Nombre de fichiers : " + mesures.nombreFichiers() + " originaux.";
            case ORANGE ->
                "Nombre de fichiers : " + mesures.nombreFichiers()
                        + " originaux, moins que les " + SEUIL_FICHIERS_CREUX
                        + " attendus pour une nuit complète.";
            case ROUGE -> "Nombre de fichiers : aucun original importé.";
        };
    }

    /// Explique le feu de **cohérence du renommage** : combien de fichiers divergent du préfixe R6.
    private static String detailRenommage(Feu feu, Mesures mesures) {
        if (feu == Feu.ROUGE) {
            int malNommes = mesures.fichiersMalNommes();
            return "Cohérence du renommage : " + malNommes + " fichier" + (malNommes > 1 ? "s" : "")
                    + " hors du préfixe attendu (R6).";
        }
        return "Cohérence du renommage : tous les fichiers suivent le préfixe attendu (R6).";
    }

    private static Feu evaluerCouverture(Mesures mesures) {
        if (mesures.moitieNuitManquante()) {
            return Feu.ROUGE;
        }
        if (mesures.ecartCouvertureMinutes() > TOLERANCE_COUVERTURE_MINUTES) {
            return Feu.ORANGE;
        }
        return Feu.VERT;
    }

    private static Feu evaluerNombre(Mesures mesures) {
        if (mesures.nombreFichiers() == 0) {
            return Feu.ROUGE;
        }
        if (mesures.nombreFichiers() < SEUIL_FICHIERS_CREUX) {
            return Feu.ORANGE;
        }
        return Feu.VERT;
    }

    private static Feu evaluerRenommage(Mesures mesures) {
        return mesures.fichiersMalNommes() > 0 ? Feu.ROUGE : Feu.VERT;
    }
}
