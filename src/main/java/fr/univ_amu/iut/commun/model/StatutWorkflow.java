package fr.univ_amu.iut.commun.model;

/// Statut d'avancement d'un `Passage` dans le workflow d'import → dépôt (C5).
///
/// Progression attendue : [#IMPORTE] → [#TRANSFORME] → [#VERIFIE] → [#PRET_A_DEPOSER] →
/// [#DEPOT_EN_COURS] → [#DEPOSE]. Le `libelle` (avec accents) est la valeur persistée.
///
/// [#DEPOT_EN_COURS] (#980) est un statut **technique** posé par le dépôt automatique VigieChiro : il
/// matérialise un téléversement entamé mais incomplet (interruption, échec partiel), **reprenable**.
/// Le marquage **manuel** « Marquer déposé » le saute (Prêt à déposer → Déposé directement).
///
/// [#RECUPERE] (#2581) n'est **pas sur ce chemin** : c'est une nuit que la synchronisation a rapatriée
/// de Vigie-Chiro, avec ses observations et son rattachement mais sans son audio. Elle n'a jamais été
/// importée ici, donc elle n'a franchi aucune des étapes précédentes. Elle rejoint [#DEPOSE] - le seul
/// endroit où la file l'accueille - quand la réactivation lui rend son son.
public enum StatutWorkflow {
    IMPORTE("Importé"),
    TRANSFORME("Transformé"),
    VERIFIE("Vérifié"),
    PRET_A_DEPOSER("Prêt à déposer"),
    DEPOT_EN_COURS("Dépôt en cours"),
    DEPOSE("Déposé"),

    /// Nuit **rapatriée de Vigie-Chiro**, jamais importée ici : elle porte ses observations et son
    /// rattachement, pas son audio. Hors de la file linéaire ; sa seule suite est [#DEPOSE], quand la
    /// réactivation lui rend son son.
    ///
    /// ⚠️ **Déclaré en dernier, et ce n'est pas indifférent.** Plusieurs endroits comparent les statuts
    /// par `ordinal()` (« au moins vérifié », « au moins transformé »). Insérer cette valeur au milieu
    /// aurait décalé ces comparaisons **en silence** : elles n'auraient rien levé, elles auraient
    /// simplement répondu autre chose.
    RECUPERE("Récupéré");

    private final String libelle;

    StatutWorkflow(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }

    /// Rang de ce statut **pour trier** des passages par avancement.
    ///
    /// Ce n'est pas `ordinal()`, et c'est tout l'intérêt. `ordinal()` reflète l'ordre de **déclaration**,
    /// qui place [#RECUPERE] en dernier - non parce qu'une nuit récupérée serait la plus avancée, mais
    /// parce qu'il a fallu l'ajouter là pour ne pas décaler les comparaisons existantes (ADR 2581). Trier
    /// là-dessus la rangerait après « Déposé » par pur effet de bord.
    ///
    /// Une nuit récupérée **est** sur la plateforme : elle se range donc avec les nuits déposées. Les
    /// départager ensuite revient au critère suivant du comparateur.
    public int rangDeProgression() {
        return this == RECUPERE ? DEPOSE.ordinal() : ordinal();
    }

    public static StatutWorkflow parLibelle(String libelle) {
        for (StatutWorkflow statut : values()) {
            if (statut.libelle.equals(libelle)) {
                return statut;
            }
        }
        throw new IllegalArgumentException("Statut workflow inconnu : " + libelle);
    }
}
