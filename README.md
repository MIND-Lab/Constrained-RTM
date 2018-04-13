# <h1 id="top">Constrained Relational Topic Models</h1>

This is an extension of Relational Topic Models, called Constrained Relational Topic Models (C-RTM). It extends the code from the package of ([Weiwei Yang](http://cs.umd.edu/~wwyang/)'s). 
C-RTM models the structure of a document network and incorporates other types of relational information obtained by prior domain knowledge.

## <h2 id="clda">Execution of the program in Command Line</h2>
```
java -cp YWWTools.jar:deps.jar yang.weiwei.Tools --tool lda --model lda --constrained true --vocab <vocab-file> --corpus <corpus-file> --trained-model <model-file>
```
- Required arguments
	- `<constrained>` `true`: it must be set to true to allow the incorporation of prior knowledge constraints.
	- `<vocab-file>`: Vocabulary file. Each line contains a unique word.
	- `<corpus-file>`: Corpus file in which documents are represented by word indexes and frequencies. Each line contains a document in the following format

		```
		<doc-len> <word-type-1>:<frequency-1> <word-type-2>:<frequency-2> ... <word-type-n>:<frequency-n>
		```
	
		`<doc-len>` is the total number of *tokens* in this document. `<word-type-i>` denotes the i-th word in `<vocab-file>`, starting from 0. Words with zero frequency can be omitted.
	- `<model-file>`: Trained model file in JSON format. Read and written by program. 
  - `--train-c-file <constraint-file>`: File containing the document constraints. Each line contains a constraint in the following format
  
    ```
    <constraint-type> <document-1> <document-2>
    ```
    
    `<document-1>` is row-id of document-1. `<document-2>` is row-id of document-2. `<constraint-type>` must be set to `M` (if it is a must-constraint) or `C` (if it is a cannot-constraint).
- Optional arguments
	- `--model <model-name>`: The topic model you want to use (default: [LDA](#lda_cmd)). Tested `<model-name>` (case unsensitive) are
		- [LDA](#lda_cmd): Constrained LDA
		- [RTM](#rtm_cmd): Constrained Relational topic model.
    - other models as extensions of LDA implemented by Weiwei Yang can be used and are already provided in the code.
  - `--newfun <newfun>`: Type of potential function of C-LDA. If true, it is normalized (as defined in ...).
	- `--no-verbose`: Stop printing log to console.
	- `--alpha <alpha-value>`: Parameter of Dirichlet prior of document distribution over topics (default: 1.0). Must be a positive real number.
	- `--beta <beta-value>`: Parameter of Dirichlet prior of topic distribution over words (default: 0.1). Must be a positive real number.
	- `--topics <num-topics>`: Number of topics (default: 10). Must be a positive integer.
	- `--iters <num-iters>`: Number of iterations (default: 100). Must be a positive integer.
	- `--update`: Update alpha while sampling (default: false). It does not work well.
	- `--update-int <update-interval>`: Interval of updating alpha (default: 10). Must be a positive integer.
	- `--theta <theta-file>`: File for document distribution over topics. Each line contains a document's topic distribution. Topic weights are separated by space.
	- `--output-topic <topic-file>`: File for showing topics.
	- `--topic-count <topic-count-file>`: File for document-topic counts.
	- `--top-word <num-top-word>`: Number of words to give when showing topics (default: 10). Must be a positive integer.
  - `--burn-in <burnin>`: Number of burn-in iterations. Default: 0.

[Back to Top](#top)
