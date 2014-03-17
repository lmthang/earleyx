The Earleyx parser was originated from Roger Levy's prefix parser, but has evolved significantly. Earleyx can generate Viterbi parses and perform rule estimation (Expectation-Maximization and Variational Bayes). The parser also implements the scaling approach as described in my TACL'13 paper which speeds up parsing time and allows for parsing long sentences (with restricted grammars).

Features:
(a) Code was restructured and rewritten to follow the flow of Stolcke's algorithm (see the method parse() in parser.EarleyParser).
(b) Scaling approach to parse long sentences (see my TACL'13 paper). With scaling, no log operations are required (see the usage of util.Operator/ProbOperator/LogProbOperator).
(c) Rule probability estimation: inside-outside algorithm in the prefix parser context as described in Stolcke's paper. Expectation-Maximization and Variational Bayes are implemented (see induction.InsideOutside).
(d) Handling of dense and sparse grammars (arrays vs lists, see parser.EarleyParserDense/EarleyParserSparse).
(e) Compute closure matrices efficiently in a way that avoids inverting large matrices as described in Stolcke's paper (see base.ClosureMatrix).
(f) Handle grammars with high fan-out (see Util.TrieSurprisal).
(g) Use integers for strings for speed.
(h) Smoothing of rule probabilities for unknown words (see parser.SmoothLexicon).

References:
(a) Andreas Stolcke. 1995. An Efficient Probabilistic Context-Free Parsing Algorithm that Computes Prefix Probabilities. Computational Linguistics 21(2), 165-201.
(b) Roger Levy's prefix parser. http://idiom.ucsd.edu/~rlevy/prefixprobabilityparser.html 
Roger Levy. 2008. Expectation-based syntactic comprehension. Cognition 106(3):1126-1177.
(c) Earleyx:
Minh-Thang Luong, Michael C. Frank, and Mark Johnson. 2013. Parsing entire discourses as very long strings: Capturing topic continuity in grounded language learning. Transactions of the Association for Computational Linguistics (TACL’13). 

Minh-Thang Luong @ 2012, 2013, 2014

/*********/
/* Files */
/*********/
README.TXT      - this file
build.xml		- Java compile file
src/            - source code
lib/            - necessary libraries (stanford javanlp-core, colt, junit)
data/           - sample data
grammars/		- sample grammars
  
/********************************/
/* Main class to run the parser */
/********************************/

Main -in inFile  -out outPrefix (-grammar grammarFile | -treebank treebankFile) -obj objectives
	[-root rootSymbol] [-io] [-sparse] [-normalprob] [-scale] [-verbose opt]
	Compulsory:
		in              	input filename, i.e. sentences to parse
		out             	output prefix to name output files.
		grammar|treebank    either read directly from a grammar file or from a treebank.For the 
							latter, a grammar file will be output as outPrefix.grammar .
		obj             	a comma separated list consitsing of any of the following values: 
							surprisal, stringprob, viterbi. Default is "surprisal,stringprob,viterbi" if -io is not specified, and "" if -io is specified. Output files will be outPrefix.obj .

	 Optional:
		root            specify the start symbol of sentences (default "ROOT")
		io              run inside-outside algorithm, output final grammar to outPrefix.io.grammar
		sparse          optimize for sparse grammars (default: run with dense grammars)
		normalprob      perform numeric computation in normal prob (cf. log-prob). 
						This switch is best to be used with -scale.
		scale           rescaling approach to parse extremely long sentences
		verbose         -1 -- no debug info (default), 0: surprisal per word, 1-4 -- increasing more details

/*******************/
/* Running example */
/*******************/
* To run the code, run ant to generate earleyx.jar

/** Standard usages **/
* The following command expects an input file and a treebank file (see data/):
  java -classpath "earleyx.jar;lib/*" parser.Main -in data/text.1 -treebank data/treebank.500 -out output/result -verbose 0

After running the above command, the parser will generate the following files in the output/ directory:
  result.surprisal: surprisal values
  result.stringprob: string probabilities
  result.viterbi: viterbi parses
  result.grammar: the grammar extracted from the treebank

* To run parser from an existing grammar rather than a tree bank, use the following command:
  java -classpath "earleyx.jar;lib/*" parser.Main -in data/text.1 -grammar output/result.grammar -out newOutput/result -verbose 0

The results generated should be the same as before:
  diff output/result.surprisal newOutput/result.surprisal
  diff output/result.stringprob newOutput/result.stringprob
  diff output/result.viterbi newOutput/result.viterbi

* To compute only surprisal values: add the option
-obj "surprisal"
By default, we have -obj "surprisal,stringprob,viterbi"

/** Inside-outside algorithm **/
* Use -io switch and -sparse (currently IO only work with EarleyParserSparse)
Since the root symbol of the grammar is "S", we add the option -root "S".

java -classpath "earleyx.jar;lib/*" parser.Main -in data/testeng.yld.a -grammar grammars/testengger.grammar -out output/result -io -sparse -root "S"

You should see the same results as below:
# iteration 1, numRules=28, sumNegLogProb = 68.46002594157635
# iteration 2, numRules=28, sumNegLogProb = 58.51055584306548
# iteration 3, numRules=28, sumNegLogProb = 55.220924477124214
# iteration 4, numRules=28, sumNegLogProb = 53.7010153144059
# iteration 5, numRules=27, sumNegLogProb = 52.26369575985821
# iteration 6, numRules=23, sumNegLogProb = 50.759354005056494
# iteration 7, numRules=23, sumNegLogProb = 50.63455865171508
# iteration 8, numRules=19, sumNegLogProb = 50.63452160196377
# iteration 9, numRules=19, sumNegLogProb = 50.63452160196377

The final grammar is outputed into output/result.iogrammar .

/****************************/
/* Concepts (to be updated) */
/****************************/
* Tags: nonterminals + preterminals
* Edge: represent an active edge used in Earley algorithms, e.g X -> a b . c
* Edge space: keeps track of edges as integers.

/*******************/
/* Other Util code */
/*******************/
* Read WSJ-format file, extract grammar rules, smooth, and output to a file
java -cp "earleyx.jar;lib/*" util.TreeBankFile -in data/treebank.5 -out grammars/wsj5.grammar -opt 1

* For social cue data, remove pseudo node, and extract out individual sentence parses if the current parse is for the whole discourse
java -cp "earleyx.jar;lib/*" util.TreeBankFile -in output/social.viterbi -out output/social.viterbi.clean -opt 3

Before:
( (Sentence (Topic.dog (T.dog (PSEUDO.DOG .dog) (Socials.Topical.kid.eyes (PSEUDOKID.EYES kid.eyes) (Socials.Topical.mom.eyes (PSEUDOMOM.EYES mom.eyes) (Socials.Topical.mom.point #)))) (Topic.None (T.None (PSEUDO.PIG .pig) (Socials.NotTopical.kid.hands (PSEUDOKID.HANDS kid.hands) (Socials.NotTopical.mom.point #))) (Topic.None ##))) (Words.dog (Word and) (Words.dog (Word whats) (Words.dog (Word that) (Words.dog (Word is) (Words.dog (Word this) (Words.dog (Word a) (Words.dog (Word puppy) (Word dog))))))))))
After:
(ROOT (Sentence (Topic.dog (T.dog .dog (Socials.Topical.kid.eyes kid.eyes (Socials.Topical.mom.eyes mom.eyes (Socials.Topical.mom.point #)))) (Topic.None (T.None .pig (Socials.NotTopical.kid.hands kid.hands (Socials.NotTopical.mom.point #))) (Topic.None ##))) (Words.dog (Word and) (Words.dog (Word whats) (Words.dog (Word that) (Words.dog (Word is) (Words.dog (Word this) (Words.dog (Word a) (Words.dog (Word puppy) (Word dog))))))))))

* Read WSJ-format file, print pretty trees to file
java -cp "earleyx.jar;lib/*" util.TreeBankFile -in output/social.viterbi.clean -out output/social.viterbi.clean.pretty -opt 2

Print
(ROOT
  (Sentence
    (Topic.dog
      (T.dog
        .dog
        (Socials.Topical.kid.eyes
          kid.eyes
          (Socials.Topical.mom.eyes
            mom.eyes
            (Socials.Topical.mom.point #))))
      (Topic.None
        (T.None
          .pig
          (Socials.NotTopical.kid.hands
            kid.hands
            (Socials.NotTopical.mom.point #)))
        (Topic.None ##)))
    (Words.dog (Word and)
      (Words.dog (Word whats)
        (Words.dog (Word that)
          (Words.dog (Word is)
            (Words.dog (Word this)
              (Words.dog (Word a)
                (Words.dog (Word puppy) (Word dog))))))))))
