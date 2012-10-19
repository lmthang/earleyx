
Minh-Thang Luong, 2012

/*********/
/* Files */
/*********/
README.TXT      - this file
src/            - source code
lib/            - necessary libraries (stanford javanlp-core, colt, junit)
data/           - sample data
  
/********************************/
/* Main class to run the parser */
/********************************/
parser.Main -in inFile -out outFile (-grammar grammarFile | -treebank treebankFile) [-id indexFileName] [-opt option] [-prob probHandling]
     option: 0 -- run with dense grammar, EarleyParserDense (default), 1 -- EarleyParserSparse (todo)
     prob: 0 -- normal (default), 1 -- scaling (todo)
     verbose: -1 -- no debug info (default), 0: surprisal per word, 1-4 -- increasing more details

/*******************/
/* Running example */
/*******************/
* To run the code, run ant to generate earleyx.jar

* The following command expects an input file and a treebank file (see data/):
  java -classpath "earleyx.jar;lib/*" parser.Main -in data/dundee.full-text.tokenized.1 -treebank data/WSJ-processed.MRG.500 -out output/result -verbose 0

After running the above command, the parser will generate the following files in the output/ directory:
  result.srprsl: surprisal values
  result.string: string probabilities
  result.SynSp: syntactic surprisal values
  result.LexSp: lexical surprisal values
  result.grammar: the grammar extracted from the treebank

* To run parser from an existing grammar rather than a tree bank, use the following command:
  java -classpath "earleyx.jar;lib/*" parser.Main -in data/dundee.full-text.tokenized.1 -grammar output/result.grammar -out newOutput/result -verbose 0

The results generated should be the same as before:
  diff output/result.srprsl newOutput/result.srprsl
  diff output/result.string newOutput/result.string
  diff output/result.SynSp newOutput/result.SynSp
  diff output/result.LexSp newOutput/result.LexSp

/********/
/* Note */
/********/
* For adaptor grammar rules, please ignore syntactic and lexical surprisals.
