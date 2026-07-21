# Objectifs qualités

Cette section formalise la **boussole qualité** du *VigieChiro Companion* : huit objectifs prioritaires qui guident les arbitrages tout au long du développement et quatre scénarios qui les concrétisent dans des situations d'usage. Ils s'appuient sur le référentiel **ISO/IEC 25010**, dont les deux pages de référence (catégories, critères) donnent le détail.

## Les 8 objectifs du projet

Les objectifs qualités priorisent ce qui compte pour ce projet, parmi les 31 critères qualité que définit la norme. Cliquez sur un objectif pour ouvrir sa fiche détaillée.

| Id | Objectif | Catégorie ISO | Scénario lié |
|---|---|---|---|
| [O1](Objectifs%20qualités/O1.md) | Portabilité multi-OS | [Portabilité](Catégorie%20de%20qualité/Portabilité.md) | - |
| [O2](Objectifs%20qualités/O2.md) | Facilité d'apprentissage | [Convivialité](Catégorie%20de%20qualité/Convivialité.md) | [SC1](Scénario/SC1.md) |
| [O3](Objectifs%20qualités/O3.md) | Tenue dans la durée | [Efficience des performances](Catégorie%20de%20qualité/Efficience%20des%20performances.md) | - |
| [O4](Objectifs%20qualités/O4.md) | Exactitude de la lecture audio ralentie | [Adéquation fonctionnelle](Catégorie%20de%20qualité/Adéquation%20fonctionnelle.md) | - |
| [O5](Objectifs%20qualités/O5.md) | Capacité CSV (milliers d'observations) | [Efficience des performances](Catégorie%20de%20qualité/Efficience%20des%20performances.md) | - |
| [O6](Objectifs%20qualités/O6.md) | Modularité pour d'autres protocoles | [Maintenabilité](Catégorie%20de%20qualité/Maintenabilité.md) | [SC3](Scénario/SC3.md) |
| [O7](Objectifs%20qualités/O7.md) | Intégrité des annotations utilisateur | [Sécurité](Catégorie%20de%20qualité/Sécurité.md) | - |
| [O8](Objectifs%20qualités/O8.md) | Confidentialité des identifiants | [Sécurité](Catégorie%20de%20qualité/Sécurité.md) | [SC2](Scénario/SC2.md) |

Un quatrième scénario, [SC4 - Internationalisation](Scénario/SC4.md), n'est rattaché à aucun objectif prioritaire : c'est une cible de portabilité culturelle envisagée pour les versions ultérieures.

## La grille ISO/IEC 25010

La norme **ISO/IEC 25010** (qui succède à **ISO/IEC 9126**) structure la qualité logicielle en quatre niveaux emboîtés :

```mermaid
flowchart LR
    A[Catégorie] -->|regroupe| B[Critère]
    B -->|priorisé en| C[Objectif projet]
    C -->|illustré par| D[Scénario]
```

- 8 **catégories** définissent les grands axes (portabilité, sécurité, convivialité…).
- 31 **critères** affinent chaque catégorie en propriétés évaluables.
- Le projet retient 8 **objectifs** qui ordonnent les critères pertinents au regard du besoin client.
- Quelques **scénarios** d'usage rendent palpables les objectifs les plus exigeants.

Aucune application réelle ne peut satisfaire les 31 critères à la fois : la démarche qualité consiste précisément à **choisir** ceux qui comptent pour le produit.

## Sommaire de la section

| Page | Contenu |
|---|---|
| [Catégories ISO 25010](Catégorie%20de%20qualité.md) | Les 8 catégories ISO 25010 (référence). |
| [Critères ISO 25010](Critère%20de%20qualité.md) | Les 31 critères ISO 25010, regroupés par catégorie. |
| [Objectifs O1..O8](Objectifs%20qualités/index.md) | Liste complète + fiche détaillée pour chaque objectif du projet. |
| [Scénarios SC1..SC4](Scénario/index.md) | Liste complète + fiche détaillée pour chaque scénario. |
