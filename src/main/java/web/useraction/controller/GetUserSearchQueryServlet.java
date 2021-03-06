package web.useraction.controller;

import db.operation.StopStem;
import db.operation.InvertedIndex;
import db.operation.pageDoc;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.htmlparser.util.ParserException;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import org.json.JSONObject;
import org.rocksdb.RocksIterator;
import java.util.Map.Entry;


import webCrawler.Crawler;

import static java.lang.Integer.parseInt;

@WebServlet(urlPatterns = {"/GetUserSearchQueryServlet"})
public class GetUserSearchQueryServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    protected Vector<String> add2Gram(Vector <String> words){
        Vector<String> gram2Words = new Vector<String>();
        gram2Words.addAll(words);
        for(int i = 0; i < words.size()-1; i++){
            gram2Words.addElement(words.get(i)+ " " + words.get(i+1));
        }
        return gram2Words;
    }
    protected Vector<String> returnWords(StopStem stopStem,Vector<String> words) throws ServletException, IOException{
        for(int i = 0; i< words.size(); i++){
            String current_word = words.get(i).toLowerCase();
            words.set(i,current_word);
            if (current_word.equals("")){
//                System.out.println("empty string");
                words.remove(i);
                i--;
                continue;
            }
            if (stopStem.isStopWord(current_word)) {
                words.remove(i);
                i--;
            }else{
                String new_word = stopStem.stem(current_word);
                words.set(i,new_word);
            }
        }
        return words;
    }

    protected int check_decimal (int number){
        int i = 1;
        while(number/10>0){
            number = number/10;
            i++;
        }
        return i;
    }

    protected Vector<Integer> process_docs_id(String db_string) throws ServletException, IOException{
        Vector<Integer> docs_id = new Vector<Integer>();
        String db_string_test = db_string;
//        System.out.println(db_string);
        try{
            while(db_string_test.indexOf("doc")>=0){
                    try {
                        String current_doc_id_test = db_string_test.substring(db_string_test.indexOf("doc"),db_string_test.indexOf(" "));
//                        System.out.println("Doc exist: "+current_doc_id_test);
                        Integer current_doc_id_test_value = Integer.parseInt(current_doc_id_test.substring(current_doc_id_test.indexOf("doc")+3));
//                        System.out.println(current_doc_id_test_value);
                        docs_id.addElement(current_doc_id_test_value);
                        db_string_test = db_string_test.substring(db_string_test.indexOf(current_doc_id_test) + current_doc_id_test.length());
                        if(db_string_test.indexOf("doc")>=0){
                            db_string_test = db_string_test.substring(db_string_test.indexOf("doc"));
                        }
//                        System.out.println(db_string_test);
                    } catch (Exception e){
                        System.out.println(e);
                    }
                }
            } catch (Exception e){
            System.out.println("process_doc_id: "+e);
        }
        return docs_id;
    }

    protected Map<String, Vector<Integer>> cluster_key_words_docs_id(Vector<String> clean_words, InvertedIndex wordIndex) throws ServletException, IOException, RocksDBException {
        Map<String ,Vector<Integer>> key_word_docs_id = new HashMap<String, Vector<Integer>>();
        for(int i = 0; i < clean_words.size(); i ++){
            try {
                byte[] key_word = clean_words.get(i).getBytes();
                String index_word_string = null;
                if(wordIndex.getDB().get(key_word)!=null){
                    index_word_string = new String(wordIndex.getDB().get(key_word));
                    Vector<Integer> docs_id = new Vector<Integer>();
                    docs_id = process_docs_id(index_word_string);
                    key_word_docs_id.put(clean_words.get(i), docs_id);
                }
//                System.out.println(clean_words.get(i)+": "+index_word_string);
            }catch (Exception e){
                System.out.println("cluster_key_words_docs_id "+e);
            }
        }
        return  key_word_docs_id;
    }

    protected int numbers_of_entry_of_db(InvertedIndex db) throws RocksDBException {
        RocksIterator iter = db.getDB().newIterator();
        int count = 0;
        iter.seekToFirst();
        while (iter.isValid()) {
            count++;
            iter.next();
        }
        return count;
    }

    public static final double log2(double f)
    {
        return Math.log(f)/Math.log(2.0);
    }

    protected Map<String, Double> terms_weight_calculation(int doc_id, InvertedIndex wordIndexDocs, InvertedIndex terms_freq) throws RocksDBException {
        String currentDoc = new String(wordIndexDocs.getDB().get(String.valueOf(doc_id).getBytes()));
        String words = currentDoc.substring(currentDoc.indexOf("words") + 6, currentDoc.indexOf("frequencies") - 2);
        String freq = currentDoc.substring(currentDoc.indexOf("frequencies") + 12, currentDoc.length() - 1);
        String[] arrWords = words.split(",");
        String[] arrFreq = freq.split(",");
        Map<String, Double> doc_terms_weight = new HashMap<String, Double>();
        try {
            int N = numbers_of_entry_of_db(wordIndexDocs);
//            System.out.println("Total Docs :"+N);
            for (int i = 0; i < arrWords.length; i++) {
//                System.out.println(arrWords[i]);
                int term_freq = Integer.parseInt(arrFreq[i].trim());
//                System.out.println("term_freq: "+String.valueOf(term_freq));
                int doc_freq_term = Integer.parseInt(new String(terms_freq.getDB().get(arrWords[i].trim().getBytes())));
//                System.out.println("doc_freq: "+String.valueOf(doc_freq_term));
                double N_div_doc = (double) N / doc_freq_term;
                double term_weight = term_freq*log2(N_div_doc);
                BigDecimal bd = new BigDecimal(term_weight).setScale(2, RoundingMode.HALF_UP);
                double modified_term_w = bd.doubleValue();
//                System.out.println("final term weight: "+String.valueOf(modified_term_w));
                doc_terms_weight.put(arrWords[i].trim(), modified_term_w);
            }
        }catch (Exception e){
            System.out.println("terms_weight_calculation: "+e);
        }
        return doc_terms_weight;
    }
    protected Map<Integer,Map<String,Double>> docs_terms_matrix(Set<Integer> docs_id_set, InvertedIndex wordIndexDocs, InvertedIndex terms_freq) throws RocksDBException {
        Map<Integer, Map<String, Double>> docs_term_matrix = new HashMap<Integer, Map<String, Double>>();
        try {
            for (Integer i : docs_id_set) {
                docs_term_matrix.put(i, terms_weight_calculation(i, wordIndexDocs, terms_freq));
            }
        }catch (Exception e){
            System.out.println("docs_terms_matrix "+e);
        }
        return docs_term_matrix;
    }

    protected double cal_sqrt_sigma(Map<String,Double> doc_terms_w){
        double sum = 0;
        Iterator map_iterator = doc_terms_w.entrySet().iterator();
        while (map_iterator.hasNext()){
            try {
                Map.Entry mapTerms = (Map.Entry) map_iterator.next();
//                System.out.println(mapTerms.getKey());
//                System.out.println(mapTerms.getValue());
                sum = sum + Math.pow((Double) mapTerms.getValue(),2);
            }catch (Exception e){
                System.out.println("cal_sqrt_sigma: "+e);
            }
        }
        return Math.sqrt(sum);
    }

    protected double cal_doc_query_sigma(Map<String,Double> doc_terms_w, Vector<String> query){
        double sum = 0;
//        for(int i =0;i<query.size();i++){
//            try {
//                System.out.println(query.get(i)+" "+doc_terms_w.get(" " + query.get(i)));
//                if(doc_terms_w.get(" " + query.get(i))!=null){
//                    sum = sum + doc_terms_w.get(" " + query.get(i));
//                }
//            }catch (Exception e){
//                System.out.println(doc_terms_w);
//                System.out.println(e);
//            }
//        }
        for(int i =0;i<query.size();i++){
            try {
//                System.out.println(query.get(i)+" "+doc_terms_w.get(query.get(i)));
                if(doc_terms_w.get(query.get(i))!=null){
                    sum = sum + doc_terms_w.get(query.get(i));
                }
            }catch (Exception e){
                System.out.println(doc_terms_w);
                System.out.println(e);
            }
        }
        return sum;
    }

    protected String[] get_words_from_page_title(int doc_id, InvertedIndex pageTitle) throws RocksDBException {
        String currentTitle = new String(pageTitle.getDB().get(String.valueOf(doc_id).getBytes()));
        String words = currentTitle.substring(currentTitle.indexOf("words") + 6, currentTitle.indexOf("frequencies") - 2);
        //System.out.println(freq);
        String[] arrWords = words.split(",");
        return arrWords;
    }

    protected int cal_page_title_query_sigma(int doc_id, InvertedIndex pageTitle, Vector<String> query) throws RocksDBException {
        int page_title_matched = 0;
        Vector<String> page_title_terms = new Vector<String>(Arrays.asList(get_words_from_page_title(doc_id, pageTitle)));
        for (int i = 0; i < page_title_terms.size(); i++){
            page_title_terms.set(i, page_title_terms.get(i).trim());
        }
//        System.out.println("Query: "+query);
//        System.out.println("Page title terms: "+page_title_terms);
        for(int i =0;i<query.size();i++){
            if(page_title_terms.contains(query.get(i))){
                page_title_matched=1;
            }
        }
//        System.out.println(page_title_matched);
        return page_title_matched;
    }

    protected double cal_query_sqrt_signma(Vector<String> query){
        return Math.sqrt(query.size());
    }


    protected Map<Integer, Double> cosin_similarity_cal_and_page_title_matched(Map<Integer,Map<String,Double>> all_rel_docs_term_w, Vector<String> query, InvertedIndex pageTitle){
        Iterator map_iterator = all_rel_docs_term_w.entrySet().iterator();
//        System.out.println(all_rel_docs_term_w);
        Map<Integer, Double> cosin_similarity_all_docs = new HashMap<Integer, Double>();
        double doc_query_sigma = 0;
        double doc_pow_2_sigma_sqrt = 0;
        double query_sqrt_sigma = 0;
        double query_title_matches = 0;
        while (map_iterator.hasNext()){
            try {
                Map.Entry mapDocsId = (Map.Entry) map_iterator.next();
//                System.out.println(mapDocsId.getKey());
                Map<String, Double> doc_terms_weight = (Map<String, Double>) mapDocsId.getValue();
//                System.out.println(doc_terms_weight);
//                System.out.println(query);
//                System.out.println("doc_id "+mapDocsId.getKey());
                try{
                doc_query_sigma = cal_doc_query_sigma(doc_terms_weight,query);
//                if(doc_query_sigma==0){
//                    System.out.println(doc_terms_weight);
//                }
//                System.out.println("doc_query_sigma "+doc_query_sigma);
                } catch (Exception e){
                    System.out.println("doc_query_sigma_in_cosine_cal");
                }
                try {
                    doc_pow_2_sigma_sqrt = cal_sqrt_sigma(doc_terms_weight);
//                    System.out.println("doc_pow_2_sigma_sqrt " + doc_pow_2_sigma_sqrt);
                }catch (Exception e){
                    System.out.println("doc_pow_2_in_cosine_sigma");
                }
                try {
                    query_sqrt_sigma = cal_query_sqrt_signma(query);
//                    System.out.println("query_sqrt_sigma " + query_sqrt_sigma);
                } catch (Exception e){
                    System.out.println("query_sqrt_sigma in cosine");
                }
                double cosin_sim = doc_query_sigma/(doc_pow_2_sigma_sqrt*query_sqrt_sigma);
                BigDecimal bd = new BigDecimal(cosin_sim).setScale(3, RoundingMode.HALF_UP);
                double modified_cosin_sim = bd.doubleValue();
                modified_cosin_sim = modified_cosin_sim+cal_page_title_query_sigma((Integer) mapDocsId.getKey(), pageTitle,query);
                cosin_similarity_all_docs.put((Integer) mapDocsId.getKey(),modified_cosin_sim);
            }catch (Exception e){
                System.out.println("cosine_similarity_cal: "+e);
            }
        }
        return cosin_similarity_all_docs;
    }
    protected Vector<String> get_top_5_frequent_words_of_doc(Integer doc_id, InvertedIndex wordIndexDocs) throws RocksDBException {
        Vector<String> get_top_5_frequent_words_doc = new Vector<String>();
        try {
            String currentDoc = new String(wordIndexDocs.getDB().get(String.valueOf(doc_id).getBytes()));
            String words = currentDoc.substring(currentDoc.indexOf("words") + 6, currentDoc.indexOf("frequencies") - 2);
            String freq = currentDoc.substring(currentDoc.indexOf("frequencies") + 12, currentDoc.length() - 1);
            String[] arrWords = words.split(",");
            String[] arrFreq = freq.split(",");
            Map<String, Integer> doc_terms_sort = new HashMap<String, Integer>();
            for(int i =0;i<arrWords.length;i++){
                doc_terms_sort.put(arrWords[i].trim(),Integer.parseInt(arrFreq[i].trim()));
            }
            //LinkedHashMap preserve the ordering of elements in which they are inserted
            LinkedHashMap<String, Integer> reverseSortedMapForTerm = new LinkedHashMap<>();
            //Use Comparator.reverseOrder() for reverse ordering
            doc_terms_sort.entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .forEachOrdered(x -> reverseSortedMapForTerm.put(x.getKey(), x.getValue()));
//            System.out.println(reverseSortedMap);
            Vector<String> the_sorted_list = new Vector<String>(reverseSortedMapForTerm.keySet());
            for(int j = 0;j<5; j++){
                get_top_5_frequent_words_doc.addElement(the_sorted_list.get(j));
            }
//            System.out.println(get_top_5_frequent_words_doc);
//            System.out.println(reverseSortedMapForTerm);
        }catch (RocksDBException r){
            System.out.println("get_top_5: "+r);
        }
        return get_top_5_frequent_words_doc;
    }


    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        System.out.println("in user search controller");
        try{
            //Open RocksDB library
            RocksDB.loadLibrary();
//            String path = "/Users/tayingcheng/Desktop/2019-2020Spring/Comp4321/project/comp_4321_project_search_engine/src/main/java/db/data/docs";
//            String path2 = "/Users/tayingcheng/Desktop/2019-2020Spring/Comp4321/project/comp_4321_project_search_engine/src/main/java/db/data/words";
            String path = getServletContext().getRealPath("WEB-INF/classes/data/words");
            String path2 = getServletContext().getRealPath("WEB-INF/classes/data/docs");
            String path3 = getServletContext().getRealPath("WEB-INF/classes/data/terms_freq");
            String path4 = getServletContext().getRealPath("WEB-INF/classes/data/titles");
            InvertedIndex wordIndex = new InvertedIndex(path);
            InvertedIndex wordIndexDocs = new InvertedIndex(path2);
            InvertedIndex terms_freq = new InvertedIndex(path3);
            InvertedIndex pages_titles = new InvertedIndex(path4);

            // prepare for query
            StopStem stopStem = new StopStem();
            Vector<String> words = null;
            Vector<String> clean_words = null;
            if(request.getParameter("page_id")!=null){
                int doc_id = Integer.parseInt(request.getParameter("page_id"));
//                System.out.println("Similar page id "+doc_id);
                words = get_top_5_frequent_words_of_doc(doc_id, wordIndexDocs);
                clean_words = returnWords(stopStem, words);
            }else {
                words = new Vector<String>(Arrays.asList(request.getParameter("search_query").split(" ")));
                clean_words = returnWords(stopStem, words);
                clean_words = add2Gram(clean_words);
            }


            //start the searching
            Map<String ,Vector<Integer>> search_query_docs_result = cluster_key_words_docs_id(clean_words, wordIndex);
            Iterator map_iterator = search_query_docs_result.entrySet().iterator();
            Set<Integer> docs_id_set = new TreeSet<Integer>();
            while (map_iterator.hasNext()){
                try {
                    Map.Entry mapDocsId = (Map.Entry) map_iterator.next();
//                    System.out.println(mapDocsId.getKey());
//                    System.out.println(mapDocsId.getValue());
                    docs_id_set.addAll((Collection<? extends Integer>) mapDocsId.getValue());
                }catch (Exception e){
                    System.out.println(e);
                }
            }
//            System.out.println(docs_id_set);
            Map<Integer,Map<String,Double>> all_rel_docs_term_weight = docs_terms_matrix(docs_id_set,wordIndexDocs,terms_freq);
            Map<Integer, Double> cosine_siml = new HashMap<Integer, Double>();
            cosine_siml = cosin_similarity_cal_and_page_title_matched(all_rel_docs_term_weight,clean_words, pages_titles);
//            System.out.println(cosine_siml);

            //LinkedHashMap preserve the ordering of elements in which they are inserted
            LinkedHashMap<Integer, Double> reverseSortedMap = new LinkedHashMap<>();
            //Use Comparator.reverseOrder() for reverse ordering
            cosine_siml.entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .forEachOrdered(x -> reverseSortedMap.put(x.getKey(), x.getValue()));
//            System.out.println(reverseSortedMap);
            Vector<Integer> tempdocs_id = new Vector<Integer>(reverseSortedMap.keySet());
            Vector<Double> tempdocs_score = new Vector<Double>(reverseSortedMap.values());
            Vector<Integer> docs_id = new Vector<Integer>();
            Vector<Double> docs_score = new Vector<Double>();
//            System.out.println(docs_id);
//            System.out.println(docs_score);
            if(tempdocs_id.size() >=50){
                for(int i = 0; i < 50; i ++){
                    docs_id.addElement(tempdocs_id.get(i));
                    docs_score.addElement(tempdocs_score.get(i));
                }
            }
            else{
                docs_id = tempdocs_id;
                docs_score = tempdocs_score;
            }
//            wordIndexDocs.returnDoc(String.valueOf("1"));
//            wordIndexDocs.returnDoc(String.valueOf("12"));
//            wordIndexDocs.returnDoc(String.valueOf("38"));

            // format the document
            Vector<String> pageTitles = new Vector<String>();
            Vector<String> pageurls = new Vector<String>();
            Vector<String>  modifiedtimes = new Vector<String>();
            Vector<String> pageSizes = new Vector<String>();
            Vector<Vector<String>> parentLinkSet = new Vector<Vector<String>>();
            Vector<Vector<String>> childLinkSets = new Vector<Vector<String>>();
            Vector<Vector<String>> wordSets = new Vector<Vector<String>>();
            Vector<Vector<String>> freqSets = new Vector<Vector<String>>();

            for(int i = 0; i < docs_id.size(); i ++){
                pageDoc newDoc = wordIndexDocs.returnDoc(String.valueOf(String.valueOf(docs_id.get(i))));
//                System.out.println(newDoc.title);
                pageTitles.addElement(newDoc.title);
                pageurls.addElement(newDoc.url);
                modifiedtimes.addElement(newDoc.modifiedTime);
                pageSizes.addElement(newDoc.pageSize);
                parentLinkSet.addElement(newDoc.parentLinks);
                childLinkSets.addElement(newDoc.childLinks);
                wordSets.addElement(newDoc.words);
                freqSets.addElement(newDoc.frequencies);
            }


            //test comment
            response.setContentType("application/json");
            response.setCharacterEncoding("utf-8");
            /* construct your json */
            JSONObject jsonResponse = new JSONObject();

//            System.out.println(newDoc.title);
            jsonResponse.put("pageTitle", pageTitles);
            jsonResponse.put("pageUrl", pageurls);
            jsonResponse.put("modTime", modifiedtimes);
            jsonResponse.put("pageSize", pageSizes);
            jsonResponse.put("parentLinks", parentLinkSet);
            jsonResponse.put("childLinks", childLinkSets);
            jsonResponse.put("words", wordSets);
            jsonResponse.put("freqs", freqSets);
            jsonResponse.put("success_message", "success");
            jsonResponse.put("docs_id", docs_id);
            jsonResponse.put("docs_score", docs_score);
            jsonResponse.put("clean_words", clean_words);
            /* send to the client the JSON string */
            response.getWriter().write(jsonResponse.toString());
            wordIndex.getDB().close();
            wordIndexDocs.getDB().close();
            terms_freq.getDB().close();
            pages_titles.getDB().close();
        }
        catch(RocksDBException e){
            System.out.println(e);
        }
    }
}
