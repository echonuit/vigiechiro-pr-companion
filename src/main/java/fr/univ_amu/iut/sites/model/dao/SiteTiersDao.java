package fr.univ_amu.iut.sites.model.dao;

import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.util.HashSet;
import java.util.Set;

/// DAO du marquage **« carré d'un tiers »** (table latérale `site_tiers`, #2525).
///
/// Table de **présence** : une ligne signifie « ce carré appartient à un autre observateur » (dérivé de
/// `site.observateur` comparé au profil connecté). L'absence de ligne = son propre carré, le cas
/// courant, qui ne coûte donc aucun stockage. Même patron que
/// [fr.univ_amu.iut.passage.model.dao.PassageOpportunisteDao] : on isole le fait hors du record
/// [fr.univ_amu.iut.sites.model.Site], construit en ~81 endroits (cf. EPIC arité #2483).
///
/// L'entité générique est ici la clé elle-même (`Long` = `site_id`) : seul le **fait d'exister** compte.
public class SiteTiersDao extends DaoGenerique<Long, Long> {

    private static final RowMapper<Long> MAPPER = rs -> rs.getLong("site_id");

    public SiteTiersDao(SourceDeDonnees source) {
        super(source);
    }

    @Override
    protected String table() {
        return "site_tiers";
    }

    @Override
    protected String colonneCle() {
        return "site_id";
    }

    @Override
    protected RowMapper<Long> mapper() {
        return MAPPER;
    }

    /// Contrat [fr.univ_amu.iut.commun.persistence.Dao] : table de **présence** (aucune colonne mutable).
    /// `insert` pose la présence (idempotent) ; à préférer via [#marquer].
    @Override
    public Long insert(Long idSite) {
        marquer(idSite);
        return idSite;
    }

    /// Contrat [fr.univ_amu.iut.commun.persistence.Dao] : sans objet sur une ligne de présence.
    /// Idempotent : équivaut à s'assurer que la présence existe.
    @Override
    public void update(Long idSite) {
        marquer(idSite);
    }

    /// Le carré `idSite` appartient-il à un tiers ?
    public boolean estTiers(long idSite) {
        return findById(idSite).isPresent();
    }

    /// Marque le carré comme appartenant à un tiers (idempotent : `ON CONFLICT DO NOTHING`).
    public void marquer(long idSite) {
        executerMaj("INSERT INTO site_tiers (site_id) VALUES (?) ON CONFLICT(site_id) DO NOTHING", idSite);
    }

    /// Retire le marquage (idempotent) : le carré redevient « le sien ».
    public void demarquer(long idSite) {
        delete(idSite);
    }

    /// Point d'entrée à privilégier : (dé)marque selon `tiers`. Appelé à chaque synchronisation, la
    /// propriété d'un carré pouvant changer côté plateforme.
    public void definir(long idSite, boolean tiers) {
        if (tiers) {
            marquer(idSite);
        } else {
            demarquer(idSite);
        }
    }

    /// Identifiants de **tous** les carrés de tiers (lecture groupée : le solde de saison les écarte en
    /// un seul accès, plutôt qu'une requête par site).
    public Set<Long> tousLesIds() {
        return new HashSet<>(findAll());
    }
}
