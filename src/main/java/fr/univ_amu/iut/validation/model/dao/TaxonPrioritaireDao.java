package fr.univ_amu.iut.validation.model.dao;

import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.util.LinkedHashSet;
import java.util.Set;

/// DAO du marquage **espèce à enjeu de conservation** (table latérale `taxon_prioritaire`, V36, #2353) :
/// les espèces dites *prioritaires* du Plan National d'Actions Chiroptères 2016-2025.
///
/// Table de **présence** : une ligne signifie « ce taxon est prioritaire ». L'absence de ligne n'est pas
/// une inconnue, c'est le cas courant — sur ~300 taxons du référentiel, 17 sont marqués. Même patron que
/// [fr.univ_amu.iut.passage.model.dao.PassageOpportunisteDao] (V34) et le marquage des carrés de tiers
/// (V35) : le record [fr.univ_amu.iut.validation.model.Taxon] ne gagne pas une 5e composante pour un
/// booléen (EPIC arité #2483).
///
/// L'entité générique est ici la clé elle-même (`String` = `taxon_code`) : seul le **fait d'exister**
/// compte.
public class TaxonPrioritaireDao extends DaoGenerique<String, String> {

    private static final RowMapper<String> MAPPER = rs -> rs.getString("taxon_code");

    public TaxonPrioritaireDao(SourceDeDonnees source) {
        super(source);
    }

    @Override
    protected String table() {
        return "taxon_prioritaire";
    }

    @Override
    protected String colonneCle() {
        return "taxon_code";
    }

    @Override
    protected RowMapper<String> mapper() {
        return MAPPER;
    }

    /// Contrat [fr.univ_amu.iut.commun.persistence.Dao] : `taxon_prioritaire` est une table de **présence**
    /// alimentée par la migration, pas par l'application. Marquer à la main reviendrait à modifier une
    /// donnée de référence depuis l'IHM : la liste vient du plan national, elle ne se négocie pas.
    @Override
    public String insert(String codeTaxon) {
        throw new UnsupportedOperationException(
                "Le marquage « espèce prioritaire » vient du référentiel PNA (V36), pas de l'application.");
    }

    /// Contrat [fr.univ_amu.iut.commun.persistence.Dao] : sans objet, pour la même raison que [#insert].
    @Override
    public void update(String codeTaxon) {
        throw new UnsupportedOperationException(
                "Le marquage « espèce prioritaire » vient du référentiel PNA (V36), pas de l'application.");
    }

    /// Codes des taxons **prioritaires**, en une lecture : les écrans en gardent un instantané et le
    /// consultent une fois par ligne affichée, ce qu'une requête par ligne ne supporterait pas.
    ///
    /// `LinkedHashSet` pour que l'ordre de lecture reste stable d'une exécution à l'autre — un ensemble
    /// qui sert aussi à écrire des libellés et des exports ne doit pas les réordonner au hasard.
    public Set<String> tousLesCodes() {
        return new LinkedHashSet<>(findAll());
    }
}
