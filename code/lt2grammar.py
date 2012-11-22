#!/usr/bin/env python
# Author: Minh-Thang Luong <luong.m.thang@gmail.com>, created on Sat, Oct 20, 2012  6:17:43 AM

"""
Module docstrings.
"""

__author__ = "Minh-Thang Luong"
__copyright__ = "Copyright \251"
__version__ = "Version 1.0"
usage = '%s %s' % (__copyright__, __author__) 

### Module imports ###
import sys
import argparse # option parsing
import re # regular expression
import string
### Global variables ###


### Class declarations ###


### Function declarations ###
def process_command_line(argv):
  """
  Return a 1-tuple: (args list).
  `argv` is a list of arguments, or `None` for ``sys.argv[1:]``.
  """
  
  parser = argparse.ArgumentParser(description=usage) # add description
  # positional arguments
  parser.add_argument('in_file', metavar='in_file', type=str, help='input file') 
  parser.add_argument('out_file', metavar='out_file', type=str, help='output file') 

  parser.add_argument('-o', '--option', dest='opt', type=int, default=0, help='option (default=0)')
  
	# version info
  parser.add_argument('-v', '--version', action='version', version=__version__ )

  # optional arguments
  parser.add_argument('-d', '--debug', dest='debug', action='store_true', default=False, help='enable debugging mode (default: false)') 
  
  args = parser.parse_args(argv)
  sys.stderr.write("# parsed arguments: %s" % str(args))

  return args

def clean_line(input_line):
  """
  Strip leading and trailing spaces
  """

  input_line = re.sub('(^\s+|\s$)', '', input_line);
  return input_line

def process_rule_line(eachline):
  eachline = clean_line(eachline)
  tokens = re.split('\s+', eachline)

  if re.search('^[0-9\.eE\+\-]+$', tokens[0]): # Dirichlet prior params
    assert re.search('^[0-9\.eE\+\-]+$', tokens[1]) # second token should be a number too
    assert len(tokens)>=5, '! wrong num tokens: %s\n' % eachline
    tag = tokens[2]
    children = tokens[4:len(tokens)]
  else:
    assert re.search('^[0-9\.eE\+\-]+$', tokens[0])==None # second token should be a number too
    assert len(tokens)>=3, '! wrong num tokens: %s\n' % eachline
    tag = tokens[0]
    children = tokens[2:len(tokens)]
  return (tag, children)

def process_files(in_file, out_file):
  """
  Read data from in_file, and output to out_file
  """

  sys.stderr.write('# in_file = %s, out_file = %s\n' % (in_file, out_file))
  inf = open(in_file, 'r')
  line_id = 0
  sys.stderr.write('# Processing file %s ...\n' % (in_file))

  ruleHash = {} # ruleHash[tag][children]
  rules = [] # rules[i] = {'tag' => ..., 'children' => ....} 
  for eachline in inf:
    (tag, children) = process_rule_line(eachline)

    # write children
    num_children = len(children)
    new_children = []

    for i in range(num_children):
      if re.search('^[A-Z]', children[i]): # non-terminal
        new_children.append(children[i])
      else: # terminal
        new_child = '_' + children[i]

        if num_children>1: # mix of terminals and non-terminals
          child_tag = 'PSEUDO' + children[i].upper() # make pre-terminal
          new_children.append(child_tag)

          # add new preterminal -> terminal rule if any
          if child_tag not in ruleHash:
            ruleHash[child_tag] = {}
            assert new_child not in ruleHash[child_tag]
            ruleHash[child_tag][new_child] = 1
          else:
            assert new_child in ruleHash[child_tag], '%s\t%s\n' % (child_tag, str(ruleHash[child_tag]))
            assert len(ruleHash[child_tag]) == 1
        else: # single terminal
          new_children.append(new_child)

    # add rule
    if tag not in ruleHash:
      ruleHash[tag] = {}
    ruleHash[tag][' '.join(new_children)] = 1
    aRule = {}
    aRule['tag'] = tag
    aRule['children'] = ' '.join(new_children)
    rules.append(aRule)

    line_id = line_id + 1
    if (line_id % 10000 == 0):
      sys.stderr.write(' (%d) ' % line_id)
  inf.close()

  for tag in ruleHash:
    prob = 1.0/len(ruleHash[tag].keys()) # uniform prob
    for children in ruleHash[tag]:
      ruleHash[tag][children] = prob

  sys.stderr.write('Done! Num lines = %d\n' % line_id)
  
  # output
  sys.stderr.write('# Output to %s ...\n' % (out_file))
  ouf = open(out_file, 'w')
  for rule in rules:
    tag = rule['tag']
    children = rule['children']
    ouf.write('%s->[%s] : %e\n' % (tag, children, ruleHash[tag][children]))

  ouf.close()

def main(argv=None):
  args = process_command_line(argv)
  
  if args.debug == True:
    sys.stderr.write('Debug mode\n')

  process_files(args.in_file, args.out_file)

  return 0 # success

if __name__ == '__main__':
  status = main()
  sys.exit(status)
