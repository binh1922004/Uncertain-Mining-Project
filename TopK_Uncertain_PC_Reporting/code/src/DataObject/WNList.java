package DataObject;


import WNTree.TPNCode;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;

public class WNList extends ArrayList<TPNCode> {
    private static final long serialVersionUID=1L;
        private double wSUP = 0;
        public void addSup(double sup){
                this.wSUP = this.wSUP + (sup);
        }

        public double getwSUP() {
                return wSUP;
        }


}
