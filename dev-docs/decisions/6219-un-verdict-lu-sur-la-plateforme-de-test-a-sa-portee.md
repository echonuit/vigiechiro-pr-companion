---
type: adr
title: "Un verdict lu sur la plateforme de test a sa portée, et le garde la confronte au tag"
status: stable
article: A3
chantier: "#6219, sous-chantier #5645"
decided_at: 2026-10-13
verification: certaine
enforced_by:
  - "CorrespondanceRecetteTest#un_verdict_lu_sur_la_plateforme_de_test_s_y_joue"
verification_note: "le garde refuse la portée à un test dont ni la classe ni la méthode ne porte le tag `plateforme-de-test` ; un cas témoin fabrique trois citations et exige que la seule usurpée soit nommée, et retirer la condition du garde le fait rougir"
relations:
  complete: ["4142-un-cas-dit-ou-se-lit-son-verdict"]
verified:
  - by: machine:ci
    at: 2026-10-13
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-13
---

# Un verdict lu sur la plateforme de test a sa portée, et le garde la confronte au tag

## Contexte

L'[ADR 4142](4142-un-cas-dit-ou-se-lit-son-verdict.md) fait dire à tout test qui cite un cas de recette
où se lit le verdict du cas. Elle lui donne deux valeurs. `A_L_ECRAN` : ce que le cas demande de
constater est ce que la caméra enregistre. `HORS_APPLICATION` : le verdict est ailleurs, le scénario n'en
joue que le bord, et une réserve sur la page du clip dit ce que le clip ne prouve pas.

Ces deux valeurs supposent qu'un test ne peut pas lire ce qui arrive à l'autre bout. C'était vrai le
22 août 2026. Depuis #6061, un navigateur ouvre la fiche web de Vigie-Chiro sur la plateforme de test,
et un test y relit ce que Companion a écrit.

Le premier test de cette sorte, `EnvoiDesMetadonneesReluSurLaFicheWebTest` (#6062), cite S4-70 et
S4-71, et aucune des deux valeurs ne le décrit :

- `A_L_ECRAN` est fausse. L'écran n'est pas celui de l'application et rien ne le filme ;
- `HORS_APPLICATION` exige une réserve sur la page d'un clip. Ce test n'a pas de clip, et il n'a rien à
  réserver sur son observable, qu'il ne bouchonne pas.

`A_L_ECRAN` passait le garde sans rien ajouter. C'était la pente : une valeur fausse qui ne coûte rien,
répétée par les trois lots qui suivent, et le compte des cas « à l'écran » ne voudrait plus rien dire.

## Décision

**1. Une troisième valeur, `SUR_LA_PLATEFORME_DE_TEST`.** Le verdict se lit hors de l'application, et le
test le lit pour de bon.

**2. Elle ne porte aucune réserve.** La règle de l'ADR 4142 ne change pas : une réserve si et seulement
si la portée est `HORS_APPLICATION`. Ici rien n'est bouchonné du côté de ce que le cas demande de
constater.

**3. Le garde la confronte à un fait.** La valeur n'est permise qu'à un test dont la classe, ou la
méthode, porte le tag `plateforme-de-test`. L'ADR 4142 notait que la portée n'avait pas de seconde
source pour la contredire quand elle est fausse. Cette valeur-ci en a une, et le garde s'en sert : sans
elle, un test bouchonné pourrait se dire lu sur la plateforme.

**4. Le relevé imprime trois comptes**, et non deux.

## Ce que la valeur ne dit pas

Elle dit où le verdict est **lu**. Elle ne dit pas par quelle surface l'écriture est **partie**. Le test
de #6062 envoie par le service que l'écran, la commande et le rattrapage appellent, et non par le bouton
de l'écran : aucun job de demande ne joue un scénario d'écran sur la plateforme de test. Cette limite
s'écrit dans le test. Une réserve sur une page de clips ne la porterait pas mieux, faute de clip.

Le tag dit que le test se joue sur la plateforme. Il ne dit pas que le test y lit le verdict du cas
qu'il cite : un test tagué pourrait citer un cas qu'il ne regarde pas. Cela vaut pour toute citation,
quelle que soit sa portée, et c'est le `Jugement` qui en répond.

## Ce qui a été écarté

- **Garder `A_L_ECRAN`.** Le garde passait. La valeur était fausse, et le serait restée pour chaque cas
  lu sur la fiche web.
- **`HORS_APPLICATION` avec une réserve.** Il aurait fallu une section de page de clips pour un test
  sans clip, ou un garde qui dispense de la page : dans les deux cas, la réserve cessait d'être ce que
  l'ADR 4142 en fait, la phrase que lit celui qui regarde une vidéo.
- **Ne pas citer les cas.** Le test aurait protégé le code, et S4-70 comme S4-71 seraient restés
  marqués « hors-portée », c'est-à-dire réputés sans couverture possible, ce qui n'est plus vrai.

## Conséquences

Les annotations existantes ne changent pas. Un cas marqué « hors-portée » parce que son verdict se lit
sur la fiche web peut désormais être cité : le marqueur se retire dans la demande qui apporte le test.
