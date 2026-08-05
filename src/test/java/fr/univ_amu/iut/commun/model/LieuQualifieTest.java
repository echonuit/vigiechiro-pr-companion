package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le contrat de [LieuQualifie], que six appelants supposent sans que rien ne le tienne.
///
/// Trouvé au PIT de la clôture du chantier #3151 : la branche « préfixe nul » était le seul survivant
/// restant, en `NO_COVERAGE`. Ce n'est pas un défensif inatteignable - c'est un **contrat écrit** dans
/// la Javadoc de la classe, dont dépend la garde que chaque appelant pose de son côté.
class LieuQualifieTest {

    @Test
    @DisplayName("Les deux étiquettes se joignent par le séparateur du dépôt")
    void deux_etiquettes() {
        assertThat(LieuQualifie.qualifier("640380", "Vallon")).isEqualTo("640380 · Vallon");
        assertThat(LieuQualifie.qualifier("640380", "A1"))
                .as("le même mécanisme sert le point, où le suffixe porte l'identité")
                .isEqualTo("640380 · A1");
    }

    @Test
    @DisplayName("Sans suffixe, le préfixe reste NU : pas de séparateur qui n'annonce rien")
    void sans_suffixe() {
        assertThat(LieuQualifie.qualifier("640380", null)).isEqualTo("640380");
        assertThat(LieuQualifie.qualifier("640380", ""))
                .as("une chaîne vide n'est pas un nom : elle ne mérite pas de séparateur non plus")
                .isEqualTo("640380");
        assertThat(LieuQualifie.qualifier("640380", "   ")).isEqualTo("640380");
    }

    @Test
    @DisplayName("Sans préfixe, il n'y a pas de lieu : la qualification rend null, pas une chaîne vide")
    void sans_prefixe() {
        // La distinction n'est pas cosmétique. Un appelant qui reçoit « » l'affiche : une cellule vide
        // se lit comme une valeur absente, ce qu'elle est. Un appelant qui reçoit `null` peut, lui,
        // décider de ne rien proposer du tout - c'est ce que fait `CriteresAnalyse.pointQualifie`, qui
        // retire l'entrée de la liste plutôt que d'y poser un lieu sans nom.
        assertThat(LieuQualifie.qualifier(null, "Vallon")).isNull();
        assertThat(LieuQualifie.qualifier(null, null)).isNull();
    }
}
