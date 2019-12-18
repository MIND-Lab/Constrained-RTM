# <h1 id="top">Constrained Relational Topic Models</h1>
Implementation of Constrained Relational Topic Models (C-RTM), proposed in the paper "Constrained Relational Topic Models" <a href="https://doi.org/10.1016/j.ins.2019.09.039">[https://doi.org/10.1016/j.ins.2019.09.039]</a> accepted in Information Sciences, 2020.
CRTM is a family of topic models that extend the well-know [Relational Topic Models (Chang, 2009)](#rtm). It models the structure of a document network and incorporates other types of relational information obtained by prior domain knowledge. This implementation extends the code from the package of ([Weiwei Yang](http://cs.umd.edu/~wwyang/)'s). 


## <h2 id="clda">Execution of the program in Command Line</h2>
```
java -cp YWWTools.jar:deps.jar yang.weiwei.Tools --tool lda --model lda --constrained true --vocab <vocab-file> --corpus <corpus-file> --trained-model <model-file>
```
- Required arguments
	- `--constrained true`: it must be set to true to allow the incorporation of prior knowledge constraints.
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
		- LDA: Constrained LDA
		- RTM: Constrained Relational topic model.
    - other models as extensions of LDA implemented by Weiwei Yang can be used and are already provided in the code.
    - `--newfun <boolean>`: Type of potential function of the constrained model. Default: `false`. If true, it is normalized. Otherwise it corresponds to the potential function described in [SC-LDA](#sclda).
    - `--lambda <lambda>`: Strength parameter for the potential function described in [SC-LDA](#sclda). It is valid only if `--newfun false`.
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

## <h2 id="datasets">Datasets</h2>
Three benchmark relational [datasets](http://www.cs.umd.edu/~sen/lbc-proj/LBC.html) are included in their related folders. They are already preprocessed and ready to be used as input for the model. 
Notice that the file `labels.txt` can be used to create the must- and cannot-constraints. Two random documents can be extracted and if their labels are the same, a must-constraint may be added to the `<constraint-file>`, otherwise a cannot-constraint may be added.

## <h2 id="references">[References](#references) </h2>
### <h3 id="sclda">[SC-LDA](#sclda): Sparse Constrained LDA </h3>

Yang, Y., Downey, D., Boyd-Graber, J.: Efficient Methods for Incorporating Knowledge into Topic Models. In: Proceedings of the 2015 Conference on Empirical Methods in Natural Language Processing (EMNLP). pp. 308-317 (2015)

### <h3 id="rtm">[RTM](#rtm): Relational Topic Models </h3>

Jonathan Chang, David M. Blei: Relational Topic Models for Document Networks. In: Proceedings of the Twelfth International Conference on Artificial Intelligence and Statistics (AISTATS) 2009: 81-88

[Back to Top](#top)
