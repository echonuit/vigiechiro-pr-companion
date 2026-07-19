package fr.univ_amu.iut.lot.model.dao;

import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.lot.model.DepotPlan;
import java.util.Optional;

/// DAO du plan de depot au niveau du **passage** (table `depot_plan`, #1993) : l'empreinte de la
/// liste source qui a servi a poser le plan, et sa date de pose.
///
/// [DepotUniteDao] suit l'avancement de chaque fichier ; celui-ci retient **de quoi le plan a ete
/// derive**. La distinction sert a la reprise : les archives sont nommees par leur rang dans une
/// partition deterministe, donc retrouver les memes identifiants ne prouve pas qu'ils designent le
/// meme contenu si la liste source a change entre-temps.
///
/// Une seule ecriture, [#enregistrer], **idempotente** : reposer un plan pour le meme passage
/// remplace l'empreinte plutot que d'echouer sur la cle primaire.
public class DepotPlanDao extends DaoGenerique<DepotPlan, Long> {

    private static final RowMapper<DepotPlan> MAPPER =
            rs -> new DepotPlan(rs.getLong("passage_id"), rs.getString("empreinte"), rs.getString("pose_le"));

    public DepotPlanDao(SourceDeDonnees source) {
        super(source);
    }

    @Override
    protected String table() {
        return "depot_plan";
    }

    @Override
    protected String colonneCle() {
        return "passage_id";
    }

    @Override
    protected RowMapper<DepotPlan> mapper() {
        return MAPPER;
    }

    @Override
    public DepotPlan insert(DepotPlan plan) {
        enregistrer(plan);
        return plan;
    }

    @Override
    public void update(DepotPlan plan) {
        enregistrer(plan);
    }

    /// Enregistre le plan du passage, en remplacant celui qui s'y trouvait. Idempotente : une reprise
    /// repose le meme plan sans erreur, et une liste source qui a change ecrase l'empreinte
    /// precedente (la comparaison a lieu **avant** l'appel, cf. #1994).
    public void enregistrer(DepotPlan plan) {
        executerMaj(
                "INSERT INTO depot_plan (passage_id, empreinte, pose_le) VALUES (?, ?, ?)"
                        + " ON CONFLICT (passage_id) DO UPDATE SET empreinte = excluded.empreinte,"
                        + " pose_le = excluded.pose_le",
                plan.passageId(),
                plan.empreinte(),
                plan.poseLe());
    }

    /// Le plan pose pour ce passage, s'il en existe un. Vide tant qu'aucun depot n'a ete entame, et
    /// apres une reinitialisation.
    public Optional<DepotPlan> parPassage(Long passageId) {
        return findById(passageId);
    }

    /// Oublie le plan du passage : appele a la reinitialisation du depot, pour que le prochain plan
    /// reparte d'une empreinte fraiche plutot que de se comparer a un lot revolu.
    public void supprimerPlan(Long passageId) {
        delete(passageId);
    }
}
