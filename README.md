# <h1 id="top">Entity Constrained Relational Topic Models</h1>

This tool includes two extensions of two well-known topic models: Latent Dirichlet Allocation (LDA) and Relational Topic Models. It extends the code from the package of ([Weiwei Yang](http://cs.umd.edu/~wwyang/)'s). 

The model is able to incorporate relationships as constraints between  concepts  (identified  in  the  form  of named-entities)  and  words. In the RTM counterpart, it also models the structure of a document network.

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
  - `"--train-v-file" <constraint-file>`: File containing the entity/word constraints. Each line contains a constraint in the following format
  
   ```
    <constraint-type> <word/entity-1>	<word/entity-2>
   ```

    `<document-1>` is row-id of document-1. `<document-2>` is row-id of document-2. `<constraint-type>` must be set to `M` (if it is a must-constraint) or `C` (if it is a cannot-constraint).
     
- Optional arguments
	`"--test-v-file" <constraint-file>`: File for the testing containing the entity/word constraints.
	- `--model <model-name>`: The topic model you want to use (default: LDA). Tested `<model-name>` (case unsensitive) are
		- [LDA](#lda_ref): Entity Constrained LDA
		- [RTM](#rtm_ref): Entity Constrained Relational topic model.
		
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

## <h3 id="rtm_cmd">Relational Topic Models</h2>
In addition to the above parameters, if `<model-name>` is set to `rtm`, then it requires the following
- Semi-optional arguments
	- `--rtm-train-graph <rtm-train-graph-file>` [optional in test]: Link file for RTM to train. Each line contains an edge in the format `node-1 \t node-2 \t weight`. Node number starts from 0. `weight` must be a non-negative integer. `weight` is either 0 or 1 and is optional. Its default value is 1 if not specified.
	- `--rtm-test-graph <rtm-test-graph-file>` [optional in training]: Link file for RTM to evaluate. Can be the same with RTM train graph. Format is the same as `<rtm-train-graph-file>`.
- Optional arguments
	- `--nu <nu-value>`: Variance of normal priors for weight vectors/matrices in RTM and its extensions (default: 1.0). Must be a positive real number.
	- `--plr-int <compute-PLR-interval>`: Interval of computing predictive link rank (default: 20). Must be a positive integer.
	- `--neg`: Sample negative links (default: false).
	- `--neg-ratio <neg-ratio>`: The ratio of number of negative links to number of positive links (default 1.0). Must be a positive real number.
	- `--pred <pred-file>`: Predicted document link probability matrix file.
	- `--reg <reg-file>`: Doc-doc regression value file.
	- `--directed`: Set all edges directed (default: false).


## <h2 id="datasets">Dasets</h2>
Three benchmark relational datasets are included in their related folders: Cora, WebKB and CiteSeer-M10. They are already preprocessed and ready to be used as input for the model. There are two versions of each dataset: the "processed" datasets are entity-annotated datasets, i.e. words have been associated with their respective named-entity and replaced by "ENTITY/name_of_the_entity".

Notice that the file `labels.txt` can be used to create the must- and cannot-constraints between documents. Two random documents can be extracted and if their labels are the same, a must-constraint may be added to the `<constraint-file>`, otherwise a cannot-constraint may be added.



### <h3 id="lda_ref">LDA: Latent Dirichlet Allocation</h3>

David M. Blei, Andrew Y. Ng, and Michael I. Jordan. 2003. Latent Dirichlet allocation. Journal of Machine Learning Research.

### <h3 id="sclda"> SC-LDA: Sparse Constrained LDA </h3>

Yang Y., Downey D., Boyd-Graber J.: Efficient Methods for Incorporating Knowledge into Topic Models. In: Proceedings of the 2015 Conference on Empirical Methods in Natural Language Processing (EMNLP). pp. 308-317 (2015)

### <h3 id="rtm_ref">RTM: Relational Topic Model</h3>

Jonathan Chang and David M. Blei. 2010. Hierarchical relational models for document networks. The Annals of Applied Statistics.

[Back to Top](#top)
