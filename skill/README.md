# Skills for Nori assistant
This tool provides an interface to create **multiplatform skills**. A skill is something that tries to provide an **answer to a request** from the user. It provides a fitness score based on user input (i.e. is it a query this skill is able to answer?) and provides the application using this library all of the needed data to respond to the user. A full skill is made up of **multiple separate components**, which process the data in a sort of *assembly line* to produce an output displayable to the user.

This repository is part of the Nori project. Open to contributions :-D

## Input recognizer
An `InputRecognizer` is the first step in the *assembly line*, and it **processes input** from the user to determine if it **matches a given set of** (*custom*) **rules**, calculating a fitness score. The score is a value ranging from `0.0` to `1.0`, where 0 means "it does not match at all" and 1 means "it matches perfectly". Values between `0.85` and `1.0` usually describe a good match, while values below `0.7` are considered a bad match. When an input recognizer is evaluated as the most suitable one, it has to generate an object of type `ResultType` containing data extracted from the input, to be used in the next steps.

Every input recognizer has a specificity (`low`, `medium` or `high`) that represents **how specific the set of rules is**. E.g. one that matches only queries about phone calls is very specific, while one that matches every question about famous people has a lower specificity. The specificity is needed to prevent conflicts between two skills that both match with an high score: the most specific is preferred.

## Intermediate processor
An `IntermediateProcessor` processes the data obtained from the previous step in the *assembly line* to produce a result to be passed to the next step. It is made for intermediate calculations, to connect to the internet and extract things, etc. Even though everything could be done in an `InputRecognizer`, it is better to keep things separate, so that `InputRecognizer`'s only purpose is to collect information from the user input.

