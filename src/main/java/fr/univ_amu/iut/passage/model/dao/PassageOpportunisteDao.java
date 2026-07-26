package fr.univ_amu.iut.passage.model.dao;

import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.util.HashSet;
import java.util.Set;

/// DAO du **marquage opportuniste** d'un passage (table latérale `passage_opportuniste`, #2525).
///
/// Table de **présence** : une ligne signifie « ce passage est opportuniste » (realise sur le carré
/// d'un tiers, donc exempté de R3/R4). L'absence de ligne = passage normal, le cas courant, qui ne
/// coûte donc aucun stockage. On isole ainsi le drapeau hors du record [fr.univ_amu.iut.passage.model.Passage]
/// (précédent V10 `passage_equipment`), pour ne pas propager une 16e composante à ses ~60 sites de
/// construction (cf. EPIC arité #2483).
///
/// L'entité générique est ici la clé elle-même (`Long` = `passage_id`) : le DAO ne transporte aucune
/// autre donnée, seul le **fait d'exister** compte.
public class PassageOpportunisteDao extends DaoGenerique<Long, Long> {

    private static final RowMapper<Long> MAPPER = rs -> rs.getLong("passage_id");

    public PassageOpportunisteDao(SourceDeDonnees source) {
        super(source);
    }

    @Override
    protected String table() {
        return "passage_opportuniste";
    }

    @Override
    protected String colonneCle() {
        return "passage_id";
    }

    @Override
    protected RowMapper<Long> mapper() {
        return MAPPER;
    }

    /// Contrat [fr.univ_amu.iut.commun.persistence.Dao] : `passage_opportuniste` est une table de
    /// **présence** (aucune colonne mutable). `insert` pose la présence (idempotent) ; à préférer via
    /// [#marquer].
    @Override
    public Long insert(Long idPassage) {
        marquer(idPassage);
        return idPassage;
    }

    /// Contrat [fr.univ_amu.iut.commun.persistence.Dao] : sans objet sur une ligne de présence (rien à
    /// mettre à jour). Idempotent : équivaut à s'assurer que la présence existe.
    @Override
    public void update(Long idPassage) {
        marquer(idPassage);
    }

    /// Le passage `idPassage` est-il marqué opportuniste ?
    public boolean estOpportuniste(long idPassage) {
        return findById(idPassage).isPresent();
    }

    /// Marque le passage comme opportuniste (idempotent : `ON CONFLICT DO NOTHING`).
    public void marquer(long idPassage) {
        executerMaj(
                "INSERT INTO passage_opportuniste (passage_id) VALUES (?) ON CONFLICT(passage_id) DO NOTHING",
                idPassage);
    }

    /// Retire le marquage opportuniste du passage (idempotent : sans effet s'il n'était pas marqué).
    public void demarquer(long idPassage) {
        delete(idPassage);
    }

    /// Point d'entrée à privilégier : (dé)marque selon `opportuniste`.
    public void definir(long idPassage, boolean opportuniste) {
        if (opportuniste) {
            marquer(idPassage);
        } else {
            demarquer(idPassage);
        }
    }

    /// Identifiants de **tous** les passages opportunistes (lecture groupée, pour éviter une requête
    /// par passage quand un traitement en balaie plusieurs : R4 sur un point, solde de saison).
    public Set<Long> tousLesIds() {
        return new HashSet<>(findAll());
    }
}
