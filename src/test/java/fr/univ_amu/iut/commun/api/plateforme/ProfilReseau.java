package fr.univ_amu.iut.commun.api.plateforme;

/// Les profils de réseau sous lesquels un test de la plateforme de test peut jouer (#6258, chantier
/// #5646).
///
/// Sur la plateforme de test, Companion joint l'API et le faux S3 par un lien local : rien n'y prend de
/// temps, et un transfert ou une durée ne montrent rien (ADR 4444, amendée par 5641). Toxiproxy, placé
/// devant les deux, rend ce lien lent, étroit ou coupé. Un profil prouve que Companion encaisse un tel
/// réseau ; il ne prouve pas que le vrai réseau se comporte comme lui, et la plateforme nationale reste
/// la vérification.
///
/// Un profil se pose le temps d'un bloc, par [PlateformeDeTest#sous(ProfilReseau)].
public enum ProfilReseau {

    /// Le lien local, sans rien d'ajouté.
    NOMINAL(0, 0),

    /// Chaque réponse de l'API et du faux S3 arrive avec [#LATENCE_MS] millisecondes de retard.
    LENT(ProfilReseau.LATENCE_MS, 0),

    /// Ce qui part vers l'API et le faux S3 ne dépasse pas [#DEBIT_KO_PAR_S] Kio par seconde : un
    /// téléversement prend le temps de ses octets.
    DEBIT_LIMITE(0, ProfilReseau.DEBIT_KO_PAR_S),

    /// Les deux mandataires sont fermés : toute connexion vers l'API ou le faux S3 échoue.
    COUPURE(0, 0);

    /// La latence du profil [#LENT], ajoutée à chaque réponse.
    static final int LATENCE_MS = 400;

    /// Le débit montant du profil [#DEBIT_LIMITE].
    static final int DEBIT_KO_PAR_S = 64;

    private final int latenceMs;

    private final int debitKoParS;

    ProfilReseau(int latenceMs, int debitKoParS) {
        this.latenceMs = latenceMs;
        this.debitKoParS = debitKoParS;
    }

    int latenceMs() {
        return latenceMs;
    }

    int debitKoParS() {
        return debitKoParS;
    }
}
