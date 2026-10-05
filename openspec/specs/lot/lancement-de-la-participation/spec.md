# lot/lancement-de-la-participation Specification

## Purpose
Demander à Vigie-Chiro d'analyser une nuit déposée, depuis un seul point d'action, et savoir aussitôt
ce que la plateforme a fait de cette demande, à l'écran comme en ligne de commande.

## Requirements

### Requirement: Un seul point d'action lance la participation

Après un dépôt connecté complet, l'écran de lot SHALL offrir un seul bouton « Lancer la
participation » : celui de la dernière étape. Le compte rendu du dépôt SHALL nommer cette prochaine étape sans
offrir de bouton qui la double.

*Vérifié par* : un test d'interface sur l'écran monté avec un dépôt (`LotDepotConnecteViewTest`), qui
compte les boutons de ce libellé après un dépôt complet.  La
capture de l'état « participation liée » le montre.

#### Scenario: Dépôt connecté complet

- **WHEN** toutes les archives d'une nuit sont en ligne et une participation est liée
- **THEN** l'écran montre un seul bouton « Lancer la participation », à la dernière étape

#### Scenario: Dépôt manuel

- **WHEN** aucune participation n'est liée au passage
- **THEN** la dernière étape offre « Marquer déposé », et aucun bouton ne propose de lancer la participation

### Requirement: Le titre de l'étape dit le geste qu'elle offre

Le titre de la dernière étape SHALL être « Lancer la participation » quand une participation est liée, et
« Marquer le passage déposé » sinon, précédé de son rang : 4 quand l'étape des archives est offerte, 3
quand elle ne l'est pas (capacité `lot/parcours-du-depot`). Il SHALL suivre ce lien quand il change, sans
réouvrir l'écran.

*Vérifié par* : le test d'interface de l'exigence précédente, qui lit aussi le titre, dans les deux
états.

#### Scenario: Participation liée

- **WHEN** une participation est liée au passage
- **THEN** le titre de la dernière étape est « Lancer la participation », précédé de son rang

### Requirement: Le résultat du lancement se dit près du bouton

Le résultat d'un lancement SHALL se dire dans la dernière étape, sous le bouton qui l'a demandé, avec un texte
distinct pour chacune des issues : demande acceptée, analyse déjà demandée, relance bloquée parce que
la nuit est déjà analysée, refus, plateforme injoignable. Un refus SHALL citer le motif que la
plateforme a donné, comme le fait la commande `lancer-traitement-vigiechiro`.

*Vérifié par* : un test d'interface qui enchaîne une demande acceptée, une analyse déjà demandée et un
refus, et lit le texte de la dernière étape après chacune.  La parité du
motif de refus se vérifie contre `LancerTraitementVigieChiroTest`, qui couvre déjà la commande.

#### Scenario: Demande acceptée

- **WHEN** l'observateur lance la participation et la plateforme accepte la demande
- **THEN** la dernière étape dit que l'analyse est demandée, qu'elle prend du temps, et qu'on peut fermer
  l'application

#### Scenario: Refus

- **WHEN** la plateforme refuse le lancement avec un motif
- **THEN** la dernière étape dit le refus et cite ce motif

### Requirement: Une analyse demandée ne s'offre pas à être relancée

Tant que l'analyse d'une nuit est demandée (planifiée, en cours, ou relancée par la plateforme), le
bouton « Lancer la participation » SHALL être désactivé, et son explication SHALL renvoyer à la carte
« Traitement Vigie-Chiro ». À la réouverture de l'écran, le dernier état relevé SHALL suffire à
restituer ce blocage, sans interroger la plateforme.

*Vérifié par* : un test d'interface qui applique un état « analyse planifiée » puis lit l'état et
l'explication du bouton, et un second qui rouvre l'écran sur un relevé enregistré.  Le cas
S4 qui suit le dépôt réel l'observe de bout en bout ; aucun banc ne simule la plateforme jusque-là.

#### Scenario: Juste après une demande acceptée

- **WHEN** la demande vient d'être acceptée et le relevé dit l'analyse planifiée
- **THEN** le bouton est grisé, et son explication renvoie à la carte du traitement

#### Scenario: Réouverture

- **WHEN** l'écran se rouvre sur une nuit dont le dernier relevé dit l'analyse en cours
- **THEN** le bouton est grisé dès l'ouverture, sans relevé réseau
