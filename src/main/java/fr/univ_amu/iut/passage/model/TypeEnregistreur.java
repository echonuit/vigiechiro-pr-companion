package fr.univ_amu.iut.passage.model;

import java.util.Optional;

/// Type d'un enregistreur, avec la valeur que la plateforme attend sous
/// `configuration.detecteur_enregistreur_type` (#6233).
///
/// La plateforme connaît une quarantaine d'appareils, ceux de la liste du formulaire web
/// (« SM4 », « Audiomoth », « Batlogger A »…). Companion n'en importe qu'un aujourd'hui, le Passive
/// Recorder. Chaque appareil qu'il apprendra à importer ajoute ici sa constante et sa valeur : c'est le
/// seul endroit du produit qui nomme un type pour la plateforme.
public enum TypeEnregistreur {

    /// Le Passive Recorder Teensy, dont le journal `LogPR<n>.txt` donne le numéro de série à l'import.
    PASSIVE_RECORDER("PassiveRecorder");

    private final String valeurPlateforme;

    TypeEnregistreur(String valeurPlateforme) {
        this.valeurPlateforme = valeurPlateforme;
    }

    /// La valeur sous laquelle la plateforme et son formulaire web désignent ce type.
    public String valeurPlateforme() {
        return valeurPlateforme;
    }

    /// Le type de l'appareil qui a produit la nuit `passage`, s'il est connu.
    ///
    /// Une nuit importée porte les paramètres d'acquisition lus sur sa carte, même sans journal, et seul
    /// le Passive Recorder s'importe : leur présence dit donc l'appareil. Une nuit rapatriée de la
    /// plateforme n'en porte pas, et son appareil reste inconnu : la règle « ne rien inventer » de
    /// l'ADR 0020 interdit alors d'en publier un.
    ///
    /// Cet indice ne vaudra plus le jour où un second appareil s'importera : c'est ici que son type se
    /// décidera, et nulle part ailleurs.
    public static Optional<TypeEnregistreur> de(Passage passage) {
        String parametres = passage.parametresAcquisition();
        return parametres == null || parametres.isBlank() ? Optional.empty() : Optional.of(PASSIVE_RECORDER);
    }
}
