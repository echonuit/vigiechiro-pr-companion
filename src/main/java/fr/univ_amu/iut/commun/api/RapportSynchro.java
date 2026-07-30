package fr.univ_amu.iut.commun.api;

/// Compte-rendu d'un rapprochement VigieChiro (#717) : ce qui a été synchronisé, pour le montrer à
/// l'utilisateur après une connexion (« référentiel à jour : N taxons »). Renvoyé par
/// [RapprochementVigieChiro#synchroniser(ClientVigieChiro)] quand il y a quelque chose à dire :
/// une synchronisation effectuée, **ou, depuis #1284, une synchronisation empêchée avec sa cause**
/// (« sites non récupérés : Vigie-Chiro injoignable ») : avant, l'empêchement était omis en silence.
///
/// @param libelle nature synchronisée, au pluriel (ex. `"taxons"`, `"sites"`)
/// @param nombre nombre d'éléments synchronisés (0 si la synchronisation a été empêchée)
/// @param souci cause de l'empêchement, ou `null` si la synchronisation a eu lieu
/// @param precision ce que le seul `nombre` tairait (#2557), ou `null` s'il se suffit
public record RapportSynchro(String libelle, int nombre, String souci, String precision) {

    /// Un rapport de synchronisation **effectuée** (souci absent).
    public RapportSynchro(String libelle, int nombre) {
        this(libelle, nombre, null, null);
    }

    /// Un rapport portant un **empêchement** (#1284).
    public RapportSynchro(String libelle, int nombre, String souci) {
        this(libelle, nombre, souci, null);
    }

    /// Synchronisation **empêchée** (plateforme injoignable, refus serveur) : rien n'a été touché
    /// (garde anti-purge), mais l'utilisateur mérite de le savoir.
    public static RapportSynchro empechee(String libelle, String souci) {
        return new RapportSynchro(libelle, 0, souci);
    }

    /// Le même rapport, avec ce que son seul compteur passerait sous silence (#2557).
    ///
    /// Un « 12 nuit(s) récupérée(s) » est **vrai** et pourtant trompeur quand quarante autres sont restées
    /// vides : le nombre dit ce qui a marché, jamais ce qui reste à faire. Une opération qui n'a pas tout
    /// fait doit le dire dans le même souffle, sinon elle se présente en succès (ADR 0008).
    public RapportSynchro avecPrecision(String precision) {
        return new RapportSynchro(libelle, nombre, souci, precision);
    }

    /// Rendu unique pour le bandeau de connexion, M-Sites et la CLI : « 385 taxons », « 12 nuit(s)
    /// récupérée(s) (40 restent à compléter) », ou « sites non récupérés (Vigie-Chiro injoignable : ...) ».
    public String enClair() {
        if (souci != null) {
            return libelle + " non récupérés (" + souci + ")";
        }
        return precision == null ? nombre + " " + libelle : nombre + " " + libelle + " (" + precision + ")";
    }
}
