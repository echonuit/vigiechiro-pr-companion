# P16 - Déclarer et retrouver son matériel 🎛

[← Retour au sommaire des parcours](index.md) · **Section B - Chaîne de production**

!!! warning "Cible non livrée"
    Ce parcours décrit un chantier ouvert, pas l'application d'aujourd'hui. Son état se lit sur
    l'issue **#3847**, qui se ferme quand il est livré : c'est la source de vérité, pas cette phrase.

> **Persona principal** : Karim (un parc de plusieurs enregistreurs tournant sur cinq chantiers).
> **Objectifs qualité visés** : opérabilité, protection contre les erreurs utilisateur.

Le modèle conceptuel décrit le matériel depuis l'origine :
[C4 - Enregistreur](../Modele%20conceptuel/C4%20-%20Enregistreur.md) mémorise l'identité du détecteur
« pour suivre la santé du matériel dans le temps » et a produit `1..N` passages ;
[C4bis - Micro](../Modele%20conceptuel/C4bis%20-%20Micro.md) est porté par un enregistreur, modifiable,
un seul actif à la fois.

La base tient les deux. L'import lit le numéro de série dans le journal du capteur et l'enregistre,
si bien qu'un parc **se constitue tout seul**, passage après passage. Rien ne le montre jamais, et
rien ne permet de déclarer qu'on a changé de micro.

Karim, lui, retape à chaque nuit la position du micro, sa hauteur et son type, pour un matériel qui
n'a pas bougé depuis la nuit précédente.

1. Karim ouvre **Réglages ▸ Matériel**. Il y trouve ses enregistreurs, **déjà présents** : ils sont
   arrivés par les imports, avec le numéro de série et la version de firmware lus dans les journaux.
2. Il corrige le modèle de l'un d'eux et lui ajoute un commentaire (« retour SAV en avril, membrane
   remplacée »).
3. Il déclare le **micro monté** sur chaque enregistreur : modèle, bande passante, date de mise en
   service, et la hauteur à laquelle il le pose habituellement.
4. En juin, il change le micro d'un enregistreur. Il déclare le nouveau ; l'ancien est **mis à
   l'écart** avec sa date de retrait, et reste attaché aux nuits qu'il a enregistrées.
5. À la saisie du passage suivant, position, hauteur et type sont **préremplis** depuis le micro
   actif, et modifiables pour cette nuit-là. Un matériel emprunté et non déclaré se saisit toujours
   librement.
6. En équipant un second poste, Karim **exporte son parc** et le réimporte, plutôt que de tout
   ressaisir.

## Ce que le chantier ne crée pas

Ni entité, ni migration structurante. Les tables existent, le code d'accès aux micros est écrit et
testé, et il n'est appelé par aucun écran ni aucun service. Le chantier branche un modèle déjà écrit
sur une interface qui n'existe pas.

C'est ce qui rend son coût faible, et c'est aussi ce qui explique pourquoi le manque n'a pas été vu
plus tôt : rien ne rougissait.

## La question ouverte

L'import écrase aujourd'hui le modèle et le commentaire d'un enregistreur déjà connu, à chaque nuit.
Faut-il qu'il continue, qu'il cesse d'écraser ce qu'un utilisateur a saisi à la main, ou qu'il signale
la divergence ? La réponse décide de ce qu'est le parc : une **vue** de ce que les journaux du capteur
ont dit, ou une **déclaration** que l'utilisateur possède.
