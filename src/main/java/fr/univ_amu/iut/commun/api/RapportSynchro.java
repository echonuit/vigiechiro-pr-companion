package fr.univ_amu.iut.commun.api;

/// Compte-rendu d'un rapprochement VigieChiro (#717) : ce qui a été synchronisé, pour le montrer à
/// l'utilisateur après une connexion (« référentiel à jour : N taxons »). Renvoyé par
/// [RapprochementVigieChiro#synchroniser(ClientVigieChiro)] quand il y a quelque chose à dire :
/// une synchronisation effectuée, **ou, depuis #1284, une synchronisation empêchée avec sa cause**
/// (« sites non récupérés : Vigie-Chiro injoignable ») — avant, l'empêchement était omis en silence.
///
/// @param libelle nature synchronisée, au pluriel (ex. `"taxons"`, `"sites"`)
/// @param nombre nombre d'éléments synchronisés (0 si la synchronisation a été empêchée)
/// @param souci cause de l'empêchement, ou `null` si la synchronisation a eu lieu
public record RapportSynchro(String libelle, int nombre, String souci) {

    /// Un rapport de synchronisation **effectuée** (souci absent).
    public RapportSynchro(String libelle, int nombre) {
        this(libelle, nombre, null);
    }

    /// Synchronisation **empêchée** (plateforme injoignable, refus serveur) : rien n'a été touché
    /// (garde anti-purge), mais l'utilisateur mérite de le savoir.
    public static RapportSynchro empechee(String libelle, String souci) {
        return new RapportSynchro(libelle, 0, souci);
    }

    /// Rendu unique pour le bandeau de connexion, M-Sites et la CLI : « 385 taxons », ou
    /// « sites non récupérés (Vigie-Chiro injoignable : ...) ».
    public String enClair() {
        return souci == null ? nombre + " " + libelle : libelle + " non récupérés (" + souci + ")";
    }
}
