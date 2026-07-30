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

    /// Ce statut est-il un **jalon** de la frise d'avancement affichée sur la fiche d'un passage ?
    ///
    /// La réponse est **explicite pour chaque valeur**, et c'est le point. La frise se construisait en
    /// parcourant l'énumération puis en retirant à la main ce qui n'en était pas : tout statut ajouté y
    /// entrait donc **par défaut**, et l'oubli ne levait rien - il ajoutait simplement une étape à une
    /// frise que personne ne relit (#2833). La bonne valeur par défaut est celle qui ne ment pas quand on
    /// l'oublie ; ici, c'est « non ».
    ///
    /// Deux valeurs n'en sont pas. [#DEPOT_EN_COURS] est **technique** (#980) : tant que le téléversement
    /// n'est pas fini, le jalon reste « Prêt à déposer ». [#RECUPERE] n'est pas sur ce chemin (#2581) :
    /// la nuit n'a franchi aucune de ces étapes, elle a sa propre frise.
    public boolean estJalon() {
        return switch (this) {
            case IMPORTE, TRANSFORME, VERIFIE, PRET_A_DEPOSER, DEPOSE -> true;
            case DEPOT_EN_COURS, RECUPERE -> false;
        };
    }

    /// La nuit est-elle **sur la plateforme**, d'une manière ou d'une autre ?
    ///
    /// Vrai pour [#DEPOSE] - nous l'y avons mise - et pour [#RECUPERE] - elle en vient. La distinction
    /// compte pour les gardes et l'affichage, mais **pas** pour la chaîne de dépôt : dans les deux cas,
    /// il n'y a plus rien à y déposer. Les deux se sont écrits `== DEPOSE` tant qu'un seul statut les
    /// recouvrait ; ce prédicat évite d'oublier le second à chaque nouvel appelant (#2581).
    public boolean estSurLaPlateforme() {
        return this == DEPOSE || this == RECUPERE;
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
